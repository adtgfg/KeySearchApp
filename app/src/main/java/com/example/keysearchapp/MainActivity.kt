package com.example.keysearchapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var targetEdit: EditText
    private lateinit var startEdit: EditText
    private lateinit var endEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var progressStatsText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startBtn = findViewById(R.id.startBtn)
        pauseBtn = findViewById(R.id.pauseBtn)
        stopBtn = findViewById(R.id.stopBtn)
        targetEdit = findViewById(R.id.targetEdit)
        startEdit = findViewById(R.id.startEdit)
        endEdit = findViewById(R.id.endEdit)
        statusText = findViewById(R.id.statusText)
        progressStatsText = findViewById(R.id.progressStatsText)
        progressBar = findViewById(R.id.progressBar)

        startBtn.setOnClickListener {
            val start = startEdit.text.toString().toLongOrNull() ?: 0L
            val end = endEdit.text.toString().toLongOrNull() ?: 1000000L
            val target = targetEdit.text.toString()
            if (target.isNotEmpty()) {
                val serviceIntent = Intent(this, SearchService::class.java)
                serviceIntent.putExtra("start", start)
                serviceIntent.putExtra("end", end)
                serviceIntent.putExtra("target", target)
                startForegroundService(serviceIntent)
                statusText.text = "بدأ البحث في الخلفية..."
            } else {
                Toast.makeText(this, "يرجى إدخال العنوان المستهدف", Toast.LENGTH_SHORT).show()
            }
        }

        pauseBtn.setOnClickListener {
            SearchService().pauseSearchNative()
            statusText.text = "تم إيقاف البحث مؤقتًا."
        }

        stopBtn.setOnClickListener {
            SearchService().stopSearchNative()
            statusText.text = "تم إيقاف البحث."
        }
    }
}
