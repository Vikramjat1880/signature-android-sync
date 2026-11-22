package com.signature.listener

import android.Manifest
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
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etBranch = findViewById<EditText>(R.id.etBranchCode)
        val etDevice = findViewById<EditText>(R.id.etDeviceCode)
        val etG1 = findViewById<EditText>(R.id.etGroup1)
        val etG2 = findViewById<EditText>(R.id.etGroup2)
        logView = findViewById<TextView>(R.id.tvLiveLogs)

        val prefs = SupabaseSyncManager.getPrefs(this)
        etBranch.setText(prefs.getString("branch_code", "BR01"))
        etDevice.setText(prefs.getString("device_code", "DEV01"))
        etG1.setText(prefs.getString("wa_group_1", ""))
        etG2.setText(prefs.getString(""wa_group_2", ""))

        findViewById<Button>(R.id.btnSaveStart).setOnClickListener {
            prefs.edit().putString("branch_code", etBranch.text.toString().trim().uppercase())
                .putString("device_code", etDevice.text.toString().trim().uppercase())
                .putString("wa_group_1", etG1.text.toString().trim())
                .putString("wa_group_2", etG2.text.toString().trim()).apply()
            requestAllPermissions()
            startSyncService()
            SupabaseSyncManager.sendHeartbeat(this)
            Toast.makeText(this, "Saved & 24/7 Live Sync Started!", Toast.LENGTH_SHORT)(hÀ €€€€€€€€€€É•™É•Í¡1½Ì ¤(€€€€€€€ô((€€€€€€€™¥¹‘Y¥•İ	å%ñ	ÕÑÑ½¸ø¡H¹¥¹‰Ñ¹9½Ñ¥™•ÍÌ¤¹Í•Ñ=¹±¥­1¥ÍÑ•¹•ÈìÍÑ…ÉÑÑ¥Ù¥Ñä¡%¹Ñ•¹Ğ¡M•ÑÑ¥¹Ì¹Q%=9}9=Q%%Q%=9}1%MQ9I}MQQ%9L¤¤ô(€€€€€€€™¥¹‘Y¥•İ	å%ñ	ÕÑÑ½¸ø¡H¹¥¹‰Ñ¹•ÍÌ¤¹Í•Ñ=¹±¥­1¥ÍÑ•¹•ÈìÍÑ…ÉÑÑ¥Ù¥Ñä¡%¹Ñ•¹Ğ¡M•ÑÑ¥¹Ì¹Q%=9}MM%	%1%Qe}MQQ%9L¤¤ô(€€€€€€€™¥¹‘Y¥•İ	å%ñ	ÕÑÑ½¸ø¡H¹¥¹‰Ñ¹	…ÑÑ•Éä¤¹Í•Ñ=¹±¥­1¥ÍÑ•¹•Èì(€€€€€€€€€€€¥˜€¡	Õ¥±¹YIM%=8¹M-}%9P€øô	Õ¥±¹YIM%=9}=L¹4¤ì(€€€€€€€€€€€€€€€Ù…°Á´€ô•ÑMåÍÑ•µM•ÉÙ¥”¡A½İ•É5…¹…•Èèé±…ÍÌ¹©…Ù„¤(€€€€€€€€€€€€€€€¥˜€ …Á´¹¥Í%¹½É¥¹	…ÑÑ•Éå=ÁÑ¥µ¥é…Ñ¥½¹Ì¡Á…­…•9…µ”¤¤ÍÑ…ÉÑÑ¥Ù¥Ñä¡%¹Ñ•¹Ğ¡M•ÑÑ¥¹Ì¹Q%=9}IEUMQ}%9=I}	QQIe}=AQ%5%iQ%=9L°UÉ¤¹Á…ÉÍ” ‰Á…­…”è‘Á…­…•9…µ”ˆ¤¤¤(€€€€€€€€€€€ô(€€€€€€€ô(€€€€€€€™¥¹‘Y¥•İ	å%ñ	ÕÑÑ½¸ø¡H¹¥¹‰Ñ¹Q•ÍÑA¥¹œ¤¹Í•Ñ=¹±¥­1¥ÍÑ•¹•Èì(€€€€€€€€€€€Ù…°Ñ•ÍÑUÑÈ€ô€ ÄÀÀÀÀÀÀÀÀÀÀÁ0€¬€¡MåÍÑ•´¹ÕÉÉ•¹ÑQ¥µ•5¥±±¥Ì ¤€”€àääääääääääå0¤¤¹Ñ½MÑÉ¥¹œ ¤(€€€€€€€€€€€MÕÁ…‰…Í•Må¹5…¹…•È¹ÁÕÍ¡QÉ…¹Í…Ñ¥½¸¡Ñ¡¥Ì°€‰A!=9}	9-}Hˆ°Ñ•ÍÑUÑÈ°€ÄÀÀ¸À°€‰QMQ}A%9ˆ°€‰Q•ÍĞÁ¥¹œ™É½´¹‘É½¥ÁÀˆ¤(€€€€€€€€€€€MÕÁ…‰…Í•Må¹5…¹…•È¹Í•¹‘!•…ÉÑ‰•…Ğ¡Ñ¡¥Ì¤(€€€€€€€€€€€Q½…ÍĞ¹µ…­•Q•áĞ¡Ñ¡¥Ì°€‰Q•ÍĞUQH€‘Ñ•ÍÑUÑÈÍ•¹ĞÑ¼A½ÉÑ…°„ˆ°Q½…ÍĞ¹19Q!}1=9¤¹Í¡½Ü ¤(€€€€€€€€€€€É•™É•Í¡1½Ì ¤(€€€€€€€ô(€€€€€€€ÍÑ…ÉÑMå¹M•ÉÙ¥” ¤(€€€ô((€€€½Ù•ÉÉ¥‘”™Õ¸½¹I•ÍÕµ” ¤ìÍÕÁ•È¹½¹I•ÍÕµ” ¤ìÉ•™É•Í¡1½Ì ¤ô(€€€ÁÉ¥Ù…Ñ”™Õ¸É•™É•Í¡1½Ì ¤ì±½Y¥•Ü¹Ñ•áĞ€ôMÕÁ…‰…Í•Må¹5…¹…•È¹•ÑAÉ•™Ì¡Ñ¡¥Ì¤¹•ÑMÑÉ¥¹œ ‰±¥Ù•}±½Ìˆ°€‰]…¥Ñ¥¹œ¸¸¸ˆ¤ô(€€€ÁÉ¥Ù…Ñ”™Õ¸ÍÑ…ÉÑMå¹M•ÉÙ¥” ¤ìÙ…°ÍÙŒ€ô%¹Ñ•¹Ğ¡Ñ¡¥Ì°Må¹½É•É½Õ¹‘M•ÉÙ¥”èé±…ÍÌ¹©…Ù„¤ì%˜	Õ¥±¹YIM%=8¹M-}%9P€øô	Õ¥±¹YIM%=9}=L¹<ÍÑ…ÉÑ½É•É½Õ¹‘M•ÉÙ¥”¡ÍÙŒ¤•±Í”ÍÑ…ÉÑM•ÉÙ¥”¡ÍÙŒ¤ô(€€€ÁÉ¥Ù…Ñ”™Õ¸É•ÅÕ•ÍÑ±±A•Éµ¥ÍÍ¥½¹Ì ¤ì(€€€€€€€Ù…°Á•ÉµÌ€ôµÕÑ…‰±•1¥ÍÑ=˜¡5…¹¥™•ÍĞ¹Á•Éµ¥ÍÍ¥½¸¹I%Y}M5L°5…¹¥™•ÍĞ¹Á•Éµ¥ÍÍ¥½¸¹I}M5L¤(€€€€€€€¥˜€¡	Õ¥±¹YIM%=8¹M-}%9P€øô	Õ¥±¹YIM%=9}=L¹Q%I5%MT¤Á•ÉµÌ¹…‘¡5…¹¥™•ÍĞ¹Á•Éµ¥ÍÍ¥½¸¹I}5%}%5L¤€z[ perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
    }
}
