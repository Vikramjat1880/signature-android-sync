package com.signature.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullBody = StringBuilder()
            var sender = "BANK_SMS"
            for (sms in messages) {
                sender = sms.displayOriginatingAddress ?: "BANK_SMS"
                fullBody.append(sms.displayMessageBody).append(" ")
            }
            val text = fullBody.toString().trim()
            val utrs = SupabaseSyncManager.extractUtrs(text)
            if (utrs.isNotEmpty()) {
                val amt = SupabaseSyncManager.extractAmount(text)
                val dir = SupabaseSyncManager.detectDirection(text)
                for (u in utrs) SupabaseSyncManager.pushTransaction(context, dir, u, amt, sender, "SMS: $text")
            }
        }
    }
}
