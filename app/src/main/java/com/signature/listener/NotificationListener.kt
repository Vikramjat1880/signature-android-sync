package com.signature.listener

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

class NotificationListener : NotificationListenerService() {
    private val bankUpiPackages = setOf("com.google.android.apps.nbu.paisa.user","com.google.android.apps.nbu.paisa.merchant","com.phonepe.app","com.phonepe.app.business","net.one97.paytm","com.paytm.business","in.org.npci.upiapp","com.csam.icici.bank.imobile","com.snapwork.hdfc","com.sbi.lotusintouch","com.axis.mobile","com.msf.kbank.mobile")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIK_TEXT)?.toString() ?: ""
        val combined = "$title $text $bigText".trim()
        if (combined.isEmpty()) return

        val prefs = SupabaseSyncManager.getPrefs(this)
        val g1Name = prefs.getString("wa_group_1", "")?.trim()?.lowercase(Locale.ROOT) ?: ""
        val g2Name = prefs.getString("wa_group_2", "")?.trim()?.lowercase(Locale.ROOT) ?: ""

        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            val titleLower = title.lowercase(Locale.ROOT)
            val channel = when {
                g1Name.isNotEmpty() && titleLower.contains(g1Name) -> "WA_GROUP_1"
                g2Name.isNotEmpty() && titleLower.contains(g2Name) -> "WA_GROUP_2"
                else -> return
            }
            val utrs = SupabaseSyncManager.extractUtrs(combined)
            if (utrs.isNotEmpty()) {
                val amt = SupabaseSyncManager.extractAmount(combined)
                for (u in utrs) SupabaseSyncManager.pushTransaction(this, channel, u, amt, title, "WA Text: $combined")
            }
            if (combined.contains("📷") || combined.lowercase(Locale.ROOT).contains("photo") || combined.lowercase(Locale.ROOT).contains("image")) {
                Handler(Looper.getMainLooper()).postDelayed({ scanLatestWhatsAppImage(this, channel, title) }, 2500)
            }
        } else if (bankUpiPackages.contains(pkg) || combined.uppercase(Locale.ROOT).contains("UPI")) {
            val utrs = SupabaseSyncManager.extractUtrs(combined)
            if (utrs.isNotEmpty()) {
                val amt = SupabaseSyncManager.extractAmount(combined)
                val dir = SupabaseSyncManager.detectDirection(combined)
                for (u in utrs) SupabaseSyncManager.pushTransaction(this, dir, u, amt, title, "APP ($pkg): $combined")
            }
        }
    }

    private fun scanLatestWhatsAppImage(ctx: Context, channel: String, groupTitle: String) {
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
                var checked = 0
                while (cursor.moveToNext() && checked < 4) {
                    checked++
                    val path = cursor.getString(1) ?: continue
                    val ageSec = (System.currentTimeMillis() / 1000L) - cursor.getLong(2)
                    if (ageSec <= 45 && path.lowercase(Locale.ROOT).contains("whatsapp")) {
                        val file = File(path)
                        if (file.exists()) {
                            val image = InputImage.fromFilePath(ctx, Uri.fromFile(file))
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).addOnSuccessListener { visionText ->
                                val ocrStr = visionText.text
                                val utrs = SupabaseSyncManager.extractUtrs(ocrStr)
                                val amt = SupabaseSyncManager.extractAmount(ocrStr)
                                for (u in utrs) SupabaseSyncManager.pushTransaction(ctx, channel, u, amt, groupTitle, "WA Slip OCR: ${ocrStr.replace("\n", " ")}")
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) { SupabaseSyncManager.saveLogLocal(ctx, "OCR Scan err: ${e.message}") }
    }
}
