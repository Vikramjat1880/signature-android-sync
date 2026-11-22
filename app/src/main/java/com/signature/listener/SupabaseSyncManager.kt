package com.signature.listener

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etBranch = findViewById<EditText>(R.id.etBranchCode)
        val etDevice = findViewById<EditText>(R.id.etDeviceCode)
        val etGroup1 = findViewById<EditText>(R.id.etWaGroup1)
        val etGroup2 = findViewById<EditText>(R.id.etWaGroup2)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val prefs = getSharedPreferences("SignatureConfig", Context.MODE_PRIVATE)
        etBranch.setText(prefs.getString("branch_code", "BR01"))
        etDevice.setText(prefs.getString("device_code", "DEV01"))
        etGroup1.setText(prefs.getString("wa_group_1", ""))
        etGroup2.setText(prefs.getString("wa_group_2", ""))

        requestRuntimePermissions()

        findViewById<Button>(R.id.btnSaveStart).setOnClickListener {
            val branch = etBranch.text.toString().trim().ifEmpty { "BR01" }
            val device = etDevice.text.toString().trim().ifEmpty { "DEV01" }
            val g1 = etGroup1.text.toString().trim()
            val g2 = etGroup2.text.toString().trim()

            prefs.edit()
                .putString("branch_code", branch)
                .putString("device_code", device)
                .putString("wa_group_1", g1)
                .putString("wa_group_2", g2)
                .apply()

            val serviceIntent = Intent(this, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            tvStatus.text = "Status: 24/7 Active Sync ($branch / $device)"
            Toast.makeText(this, "Config Saved & 24/7 Listener Started!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPermNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnPermAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnPermBattery).setOnClickListener {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Battery optimization already disabled!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.btnTestPing).setOnClickListener {
            val sampleMsg = "Rs. 500.00 credited to A/c XX1234 via UPI Ref No 612345678901 - TEST PING"
            SupabaseSyncManager.processAndSync(this, "TEST_PING", "SignatureApp", sampleMsg)
            Toast.makeText(this, "Test UTR Ping Sent! Check Signature Portal.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }
}
