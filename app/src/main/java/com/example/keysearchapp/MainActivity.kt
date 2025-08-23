package com.example.keysearchapp

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var targetEdit: TextInputEditText
    private lateinit var startEdit: TextInputEditText
    private lateinit var endEdit: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var progressStatsText: TextView
    private lateinit var startBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button

    // ViewModel to hold the state
    private val viewModel: SearchViewModel by viewModels()

    // Native functions (declarations stay same)
    private external fun startSearchNative(start: Long, end: Long, targetAddress: String, callback: KeySearchCallback)
    private external fun pauseSearchNative()
    private external fun resumeSearchNative()
    private external fun stopSearchNative()

    // Callback from C++
    private val keySearchCallback = object : KeySearchCallback {
        override fun onKeyFound(key: String) {
            runOnUiThread { viewModel.onKeyFound(key) }
        }
        override fun onProgressUpdate(keysChecked: Long) {
            runOnUiThread { viewModel.onProgressUpdate(keysChecked) }
        }
        override fun onSearchFinished() {
            runOnUiThread { viewModel.onSearchFinished() }
        }
        override fun getExternalFilesDir(type: String?): File? {
            return super@MainActivity.getExternalFilesDir(type)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        setupClickListeners()
        observeViewModel()

        if (viewModel.searchState.value == SearchViewModel.State.SEARCHING) {
            viewModel.forceStopState()
            Toast.makeText(this, "Search was interrupted by configuration change.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViews() {
        targetEdit = findViewById(R.id.targetEdit)
        startEdit = findViewById(R.id.startEdit)
        endEdit = findViewById(R.id.endEdit)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        progressStatsText = findViewById(R.id.progressStatsText)
        startBtn = findViewById(R.id.startBtn)
        pauseBtn = findViewById(R.id.pauseBtn)
        stopBtn = findViewById(R.id.stopBtn)
    }

    private fun observeViewModel() {
        viewModel.searchState.observe(this) { state ->
            updateUiForState(state)
        }
        viewModel.progress.observe(this) { progress ->
            progressBar.progress = progress
        }
        viewModel.statsText.observe(this) { stats ->
            progressStatsText.text = stats
        }
        viewModel.foundKey.observe(this) { key ->
            if (key != null) {
                Toast.makeText(this, "تم العثور على المفتاح وحفظه!", Toast.LENGTH_LONG).show()
                progressStatsText.text = "المفتاح (HEX): $key"
            }
        }
    }

    private fun setupClickListeners() {
        startBtn.setOnClickListener {
            if (validateInputs()) {
                val target = targetEdit.text.toString().trim()
                val startKey = startEdit.text.toString().toLong()
                val endKey = endEdit.text.toString().toLong()

                // Attempt to load native library here (delayed load).
                try {
                    System.loadLibrary("native-lib")
                } catch (t: Throwable) {
                    // If library not found or load failed, show clear message instead of crash.
                    Toast.makeText(this, "Failed to load native library: ${t.message}", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                // Wrap native call in try/catch to prevent native exceptions from killing app silently
                try {
                    viewModel.startSearch(endKey - startKey + 1)
                    startSearchNative(startKey, endKey, target, keySearchCallback)
                } catch (e: UnsatisfiedLinkError) {
                    Toast.makeText(this, "Native method not found: ${e.message}", Toast.LENGTH_LONG).show()
                    viewModel.forceStopState()
                } catch (e: Throwable) {
                    Toast.makeText(this, "Error starting native search: ${e.message}", Toast.LENGTH_LONG).show()
                    viewModel.forceStopState()
                }
            }
        }

        pauseBtn.setOnClickListener {
            if (viewModel.searchState.value == SearchViewModel.State.PAUSED) {
                try {
                    resumeSearchNative()
                    viewModel.setPaused(false)
                } catch (t: Throwable) {
                    Toast.makeText(this, "Error resuming native search: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    pauseSearchNative()
                    viewModel.setPaused(true)
                } catch (t: Throwable) {
                    Toast.makeText(this, "Error pausing native search: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        stopBtn.setOnClickListener {
            try {
                stopSearchNative()
                statusText.text = "جاري الإيقاف..."
            } catch (t: Throwable) {
                Toast.makeText(this, "Error stopping native search: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateInputs(): Boolean {
        targetEdit.error = null
        startEdit.error = null
        endEdit.error = null

        if (targetEdit.text.isNullOrBlank()) {
            targetEdit.error = "العنوان مطلوب"
            return false
        }
        val start = startEdit.text.toString().toLongOrNull()
        if (start == null) {
            startEdit.error = "قيمة بداية غير صالحة"
            return false
        }
        val end = endEdit.text.toString().toLongOrNull()
        if (end == null) {
            endEdit.error = "قيمة نهاية غير صالحة"
            return false
        }
        if (start > end) {
            endEdit.error = "النهاية يجب أن تكون أكبر من البداية"
            return false
        }
        return true
    }

    private fun updateUiForState(state: SearchViewModel.State) {
        when (state) {
            SearchViewModel.State.STOPPED -> {
                statusText.text = "جاهز للبدء"
                startBtn.isEnabled = true
                pauseBtn.isEnabled = false
                stopBtn.isEnabled = false
                targetEdit.isEnabled = true
                startEdit.isEnabled = true
                endEdit.isEnabled = true
                pauseBtn.text = "إيقاف مؤقت"
            }
            SearchViewModel.State.SEARCHING -> {
                statusText.text = "جارٍ البحث..."
                startBtn.isEnabled = false
                pauseBtn.isEnabled = true
                stopBtn.isEnabled = true
                targetEdit.isEnabled = false
                startEdit.isEnabled = false
                endEdit.isEnabled = false
                pauseBtn.text = "إيقاف مؤقت"
            }
            SearchViewModel.State.PAUSED -> {
                statusText.text = "متوقف مؤقتًا"
                pauseBtn.text = "استئناف"
            }
            SearchViewModel.State.FOUND -> {
                 statusText.text = "🎉 تم العثور على المفتاح!"
                 startBtn.isEnabled = false
                 pauseBtn.isEnabled = false
                 stopBtn.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
           try {
               stopSearchNative()
           } catch (_: Throwable) { /* ignore */ }
        }
        super.onDestroy()
    }

    // Callback interface must be accessible by native code
    interface KeySearchCallback {
        fun onKeyFound(key: String)
        fun onProgressUpdate(keysChecked: Long)
        fun onSearchFinished()
        fun getExternalFilesDir(type: String?): File?
    }
}
