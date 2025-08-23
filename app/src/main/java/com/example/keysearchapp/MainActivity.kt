package com.example.keysearchapp

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: SearchViewModel
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var stopButton: Button
    private lateinit var progressText: TextView
    private lateinit var targetInput: EditText
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText

    companion object {
        // ✅ تعديل هنا: تحميل مكتبة native-lib فقط
        // OpenSSL (ssl, crypto) يتم تحميلها بشكل تبعي من jniLibs
        init {
            System.loadLibrary("native-lib")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        pauseButton = findViewById(R.id.pauseButton)
        resumeButton = findViewById(R.id.resumeButton)
        stopButton = findViewById(R.id.stopButton)
        progressText = findViewById(R.id.progressText)
        targetInput = findViewById(R.id.targetInput)
        startInput = findViewById(R.id.startInput)
        endInput = findViewById(R.id.endInput)

        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]

        // ✅ ربط LiveData مع الواجهة
        viewModel.progress.observe(this) { progress ->
            progressText.text = "Keys checked: $progress"
        }

        viewModel.foundKey.observe(this) { key ->
            Toast.makeText(this, "Key Found: $key", Toast.LENGTH_LONG).show()
        }

        viewModel.isFinished.observe(this) {
            Toast.makeText(this, "Search Finished", Toast.LENGTH_SHORT).show()
        }

        // ✅ أزرار التحكم
        startButton.setOnClickListener {
            val start = startInput.text.toString().toLongOrNull() ?: 0L
            val end = endInput.text.toString().toLongOrNull() ?: 1000000L
            val target = targetInput.text.toString()

            if (target.isNotEmpty()) {
                viewModel.startSearch(start, end, target, this)
            } else {
                Toast.makeText(this, "Please enter target address", Toast.LENGTH_SHORT).show()
            }
        }

        pauseButton.setOnClickListener {
            viewModel.pauseSearch()
        }

        resumeButton.setOnClickListener {
            viewModel.resumeSearch()
        }

        stopButton.setOnClickListener {
            viewModel.stopSearch()
        }
    }

    // ✅ تعريف الدوال التي يستدعيها C++ عبر JNI
    external fun startSearchNative(start: Long, end: Long, target: String, callback: Any)
    external fun pauseSearchNative()
    external fun resumeSearchNative()
    external fun stopSearchNative()

    // ✅ Callbacks التي يستدعيها الكود C++
    fun onKeyFound(key: String) {
        runOnUiThread {
            viewModel.onKeyFound(key)
        }
    }

    fun onProgressUpdate(keysChecked: Long) {
        runOnUiThread {
            viewModel.onProgressUpdate(keysChecked)
        }
    }

    fun onSearchFinished() {
        runOnUiThread {
            viewModel.onSearchFinished()
        }
    }
}
