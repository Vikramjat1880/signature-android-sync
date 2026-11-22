package com.signature.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    private val paymentPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.nbu.paisa.merchant",
        "com.phonepe.app",
        "com.phonepe.app.business",
        "net.one97.paytm",
        "com.paytm.business",
        "com.bharatpe.app",
        "in.org.npci.upiapp",
        "com.dreamplug.androidapp",
        "com.axis.mobile",
        "com.icicibank.pockets",
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.snapwork.hdfc",
        "com.msf.kbank.mobile",
        "com.bankofbaroda.mconnect"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        val fullBody = "$title | $bigText".trim()

        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            val allowedGroups = SupabaseSyncManager.getAllowedGroups(this)
            val isGroupMatch = allowedGroups.any { title.contains(it, ignoreCase = true) }
            if (isGroupMatch && fullBody.isNotBlank()) {
                Log.d("SignatureWA", "Matched WA Group [$title]: $fullBody")
                SupabaseSyncManager.processAndSync(this, "WHATSAPP_MSG", title, fullBody)
            }
            return
        }

        if (paymentPackages.contains(pkg) || fullBody.contains("credited", true) || fullBody.contains("received", true) || fullBody.contains("UTR", true)) {
            if (fullBody.isNotBlank()) {
                Log.d("SignatureUPI", "Payment Notif [$pkg]: $fullBody")
                SupabaseSyncManager.processAndSync(this, "UPI_NOTIFICATION", pkg, fullBody)
            }
        }
    }
}
