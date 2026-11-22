package com.signature.listener

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SupabaseSyncManager {

    private const val SUPABASE_URL = "https://YOUR_PROJECT_ID.supabase.co"
    private const val SUPABASE_KEY = "YOUR_SUPABASE_ANON_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getAllowedGroups(context: Context): List<String> {
        val prefs = context.getSharedPreferences("SignatureConfig", Context.MODE_PRIVATE)
        val g1 = prefs.getString("wa_group_1", "")?.trim() ?: ""
        val g2 = prefs.getString("wa_group_2", "")?.trim() ?: ""
        return listOf(g1, g2).filter { it.isNotEmpty() }
    }

    fun processAndSync(context: Context, sourceType: String, sender: String, rawText: String) {
        val utr = extractUtr(rawText) ?: return
        val amount = extractAmount(rawText)
        val direction = detectDirection(rawText)
        pushTransaction(context, utr, amount, direction, sourceType, sender, rawText)
    }

    fun pushTransaction(
        context: Context,
        utr: String,
        amount: String = "0",
        direction: String = "CREDIT",
        sourceType: String = "SMS",
        sender: String = "Bank",
        rawText: String = ""
    ) {
        val prefs = context.getSharedPreferences("SignatureConfig", Context.MODE_PRIVATE)
        val branchCode = prefs.getString("branch_code", "BR01") ?: "BR01"
        val deviceCode = prefs.getString("device_code", "DEV01") ?: "DEV01"
        val amtDouble = amount.replace(",", "").toDoubleOrNull() ?: 0.0

        val payload = JSONObject().apply {
            put("utr_number", utr)
            put("amount", amtDouble)
            put("direction", direction)
            put("source_type", sourceType)
            put("sender", sender)
            put("raw_message", rawText.take(500))
            put("branch_code", branchCode)
            put("device_code", deviceCode)
        }

        Thread {
            try {
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/utr_logs")
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("SupabaseSync", "UTR $utr synced: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Sync failed for UTR $utr: ${e.message}")
            }
        }.start()
    }

    fun extractUtr(text: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:UTR|Ref(?:\\s*No)?|UPI\\s*Ref|RRN|Txn\\s*ID)[:\\s#-]*([0-9]{12})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([0-9]{12})\\b")
        )
        for (p in patterns) {
            val m = p.matcher(text)
            if (m.find()) return m.group(1)
        }
        return null
    }

    fun extractUTR(text: String): String? = extractUtr(text)

    fun extractAmount(text: String): String {
        val p = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(text)
        if (m.find()) {
            return m.group(1)?.replace(",", "") ?: "0"
        }
        return "0"
    }

    fun detectDirection(text: String): String {
        val lower = text.lowercase()
        return if (lower.contains("debited") || lower.contains("sent") || lower.contains("paid to") || lower.contains("withdrawn")) {
            "DEBIT"
        } else {
            "CREDIT"
        }
    }
}
