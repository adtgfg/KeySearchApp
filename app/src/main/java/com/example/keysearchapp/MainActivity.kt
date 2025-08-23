package com.example.keysearchapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var resumeBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var targetEdit: EditText
    private lateinit var startEdit: EditText
    private lateinit var endEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var progressStatsText: TextView
    private lateinit var progressBar: ProgressBar

    private val requestPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false) {
            Toast.makeText(this, "يجب منح صلاحية الوصول للملفات!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startBtn = findViewById(R.id.startBtn)
        pauseBtn = findViewById(R.id.pauseBtn)
        resumeBtn = findViewById(R.id.resumeBtn)
        stopBtn = findViewById(R.id.stopBtn)
        targetEdit = findViewById(R.id.targetEdit)
        startEdit = findViewById(R.id.startEdit)
        endEdit = findViewById(R.id.endEdit)
        statusText = findViewById(R.id.statusText)
        progressStatsText = findViewById(R.id.progressStatsText)
        progressBar = findViewById(R.id.progressBar)

        checkAndRequestPerms()

        startBtn.setOnClickListener {
            val start = startEdit.text.toString().toLongOrNull() ?: 0L
            val end = endEdit.text.toString().toLongOrNull() ?: 1000000L
            val target = targetEdit.text.toString()
            if (target.isNotEmpty()) {
                val serviceIntent = Intent(this, SearchService::class.java).apply {
                    action = "START"
                    putExtra("start", start)
                    putExtra("end", end)
                    putExtra("target", target)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                statusText.text = "بدأ البحث في الخلفية..."
            } else {
                Toast.makeText(this, "يرجى إدخال العنوان المستهدف", Toast.LENGTH_SHORT).show()
            }
        }

        pauseBtn.setOnClickListener {
            sendCommandToService("PAUSE")
            statusText.text = "تم إيقاف البحث مؤقتًا."
        }

        resumeBtn.setOnClickListener {
            sendCommandToService("RESUME")
            statusText.text = "تم استئناف البحث."
        }

        stopBtn.setOnClickListener {
            sendCommandToService("STOP")
            statusText.text = "تم إيقاف البحث."
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter("SEARCH_UPDATE"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun sendCommandToService(action: String) {
        val intent = Intent(this, SearchService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(this, intent)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                "SEARCH_UPDATE" -> {
                    val progress = intent.getLongExtra("progress", -1)
                    val foundKey = intent.getStringExtra("foundKey")
                    val finished = intent.getBooleanExtra("finished", false)

                    if (progress >= 0) {
                        progressStatsText.text = "تم فحص: $progress مفتاح"
                        progressBar.isIndeterminate = false
                        progressBar.progress = (progress % 100).toInt()
                    }

                    if (foundKey != null) {
                        statusText.text = "تم العثور على المفتاح: $foundKey"
                    }

                    if (finished) {
                        statusText.text = "انتهى البحث"
                    }
                }
            }
        }
    }

    private fun checkAndRequestPerms() {
        if (Build.VERSION.SDK_INT < 30) {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val need = perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (need) requestPerm.launch(perms)
        }
    }
}