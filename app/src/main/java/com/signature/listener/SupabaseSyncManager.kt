package com.signature.listener

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SupabaseSyncManager {
    private const val SUPABASE_URL = "https://gutxudaauqaegfhqpdlt.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imd1dHh1ZGFhdXFhZWdmaHFwZGx0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUzNjc1NTIsImV4cCI6MjExMDk0MzU1Mn0.M-R-dQFPBTVEfRBDBuZNBNmWUT4tKsgu3FWueHjHxYE"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val recentCache = LinkedHashSet<String>()

    fun getPrefs(ctx: Context) = ctx.getSharedPreferences("SignaturePrefs", Context.MODE_PRIVATE)

    fun extractUtrs(text: String): List<String> {
        val list = mutableListOf<String>()
        val m = Pattern.compile("\\b[0-9]{12}\\b").matcher(text)
        while (m.find()) {
            val u = m.group()
            if (u.toSet().size > 2 && !list.contains(u)) list.add(u)
        }
        return list
    }

    fun extractAmount(text: String): Double {
        val m = Pattern.compile("(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(text)
        if (m.find()) return m.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val m2 = Pattern.compile("\\b([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\b").matcher(text)
        while (m2.find()) {
            val raw = m2.group(1)?.replace(",", "") ?: ""
            if (raw.length in 1..8) {
                val v = raw.toDoubleOrNull() ?: 0.0
                if (v > 0) return v
            }
        }
        return 0.0
    }

    fun detectDirection(text: String): String {
        val u = text.uppercase(Locale.ROOT)
        if (listOf("DEBITED", "SENT", "PAID TO", "WITHDRAWN", "DR ", "TRANSFER TO", "OUTFLOW").any { u.contains(it) }) return "PHONE_BANK_DR"
        return "PHONE_BANK_CR"
    }

    fun pushTransaction(ctx: Context, channel: String, utr: String, amount: Double, sender: String, narration: String) {
        val prefs = getPrefs(ctx)
        val branch = prefs.getString("branch_code", "BR01")?.trim()?.uppercase(Locale.ROOT) ?: "BR01"
        val device = prefs.getString("device_code", "DEV01")?.trim()?.uppercase(Locale.ROOT) ?: "DEV01"

        val dedupKey = "$channel|$utr"
        synchronized(recentCache) {
            if (recentCache.contains(dedupKey)) return
            recentCache.add(dedupKey)
            if (recentCache.size > 500) {
                val it = recentCache.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
        }

        val json = JSONObject().apply {
            put("branch_code", branch)
            put("source_channel", channel)
            put("device_code", device)
            put("utr", utr)
            put("amount", amount)
            put("sender_or_user", sender.take(80))
            put("raw_narration", narration.take(240))
        }

        saveLogLocal(ctx, "[$channel] UTR: $utr | ₹$amount | $sender")
        enqueueAndFlush(ctx, json)
    }

    private fun enqueueAndFlush(ctx: Context, newObj: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = getPrefs(ctx)
            val arr = try { JSONArray(prefs.getString("offline_queue", "[]")) } catch (e: Exception) { JSONArray() }
            if (newObj != null) arr.put(newObj)
            val remaining = JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (!postToSupabase("live_transactions", item.toString())) remaining.put(item)
            }
            prefs.edit().putString("offline_queue", remaining.toString()).apply()
        }
    }

    fun flushQueueOnly(ctx: Context) { enqueueAndFlush(ctx, null) }

    fun sendHeartbeat(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {

            val prefs = getPrefs(ctx)
            val device = prefs.getString("device_code", "DEV01")?.trim()?.uppercase(Locale.ROOT) ?: "DEV01"
            val bat = getBatteryPercentage(ctx)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val payload = JSONObject().apply {
                put("last_seen", sdf.format(Date()))
                put("battery_level", bat)
            }
            patchSupabase("devices", "device_code=eq.$device", payload.toString())
        }
    }

    private fun getBatteryPercentage(ctx: Context): Int {
        return try {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            if (level >= 0 && scale > 0) (level * 100) / scale else 100
        } catch (e: Exception) { 100 }
    }

    private fun postToSupabase(table: String, bodyStr: String): Boolean {
        return try {
            val req = Request.Builder().url("$SUPABASE_URL/rest/v1/$table")
                .addHeader("apikey", SUPABASE_KEY).addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal")
                .post(bodyStr.toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    private fun patchSupabase(table: String, query: String, bodyStr: String): Boolean {
        return try {
            val req = Request.Builder().url("$SUPABASE_URL/rest/v1/$table?$query")
                .addHeader("apikey", SUPABASE_KEY).addHeader("Authorization", "Bearer $UPABASE_KEY")
                .addHeader("Content-Type", "application/json")
                .patch(bodyStr.toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    fun saveLogLocal(ctx: Context, line: String) {
        val prefs = getPrefs(ctx)
        val ts = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
        val old = prefs.getString("live_logs", "") ?: ""
        val updated = ("$ts → $line\n" + old).take(3500)
        prefs.edit().putString("live_logs", updated).apply()
    }
}
