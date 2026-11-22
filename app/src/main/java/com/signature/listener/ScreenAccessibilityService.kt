package com.signature.listener

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class ScreenAccessibilityService : AccessibilityService() {
    private val allowedPkgs = setOf("com.google.android.apps.nbu.paisa.merchant","com.google.android.apps.nbu.paisa.user","com.phonepe.app.business","com.paytm.business")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!allowedPkgs.contains(pkg)) return
        val root = rootInActiveWindow ?: return

        val sb = StringBuilder()
        collectText(root, sb)
        val screenText = sb.toString()
        val upper = screenText.uppercase(Locale.ROOT)
        if (upper.contains("UTR") || upper.contains("UPI") || upper.contains("RECEIVED") || upper.contains("PAID")) {
            val utrs = SupabaseSyncManager.extractUtrs(screenText)
            if (utrs.isNotEmpty()) {
                val amt = SupabaseSyncManager.extractAmount(screenText)
                val dir = SupabaseSyncManager.detectDirection(screenText)
                for (u in utrs) SupabaseSyncManager.pushTransaction(this, dir, u, amt, "SCREEN_CAPTURE", "Screen: ${screenText.take(160)}")
            }
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (node.text != null) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb)
    }

    override fun onInterrupt() {}
}
