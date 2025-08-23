package com.example.keysearchapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchViewModel : ViewModel() {

    enum class State { STOPPED, SEARCHING, PAUSED, FOUND }

    private val _searchState = MutableLiveData(State.STOPPED)
    val searchState: LiveData<State> = _searchState

    private val _progress = MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val _statsText = MutableLiveData("Waiting for search to start...")
    val statsText: LiveData<String> = _statsText

    private val _foundKey = MutableLiveData<String?>(null)
    val foundKey: LiveData<String?> = _foundKey

    private var totalKeys: Long = 0
    private var searchStartTime: Long = 0

    // ربط MainActivity لتتمكن من استدعاء دوال native
    private var mainActivity: MainActivity? = null

    // بدء البحث واستدعاء الدالة الأصلية
    fun startSearch(start: Long, end: Long, target: String, activity: MainActivity) {
        mainActivity = activity
        totalKeys = end - start + 1
        searchStartTime = System.currentTimeMillis()
        _searchState.value = State.SEARCHING
        _progress.value = 0
        _statsText.value = "Starting up..."
        _foundKey.value = null
        // استدعاء البحث من native-lib
        mainActivity?.startSearchNative(start, end, target, mainActivity!!)
    }

    // إيقاف مؤقت للبحث
    fun pauseSearch() {
        mainActivity?.pauseSearchNative()
        _searchState.value = State.PAUSED
        _statsText.value = "تم إيقاف البحث مؤقتًا."
    }

    // استئناف البحث
    fun resumeSearch() {
        mainActivity?.resumeSearchNative()
        _searchState.value = State.SEARCHING
        _statsText.value = "تم استئناف البحث."
    }

    // إيقاف البحث نهائيًا
    fun stopSearch() {
        mainActivity?.stopSearchNative()
        _searchState.value = State.STOPPED
        _statsText.value = "تم إيقاف البحث من قبل المستخدم."
    }

    // عند إيجاد مفتاح
    fun onKeyFound(key: String) {
        _searchState.value = State.FOUND
        _foundKey.value = key
        _progress.value = 100
        _statsText.value = "تم إيجاد المفتاح!"
    }

    // تحديث التقدم
    fun onProgressUpdate(keysChecked: Long) {
        if (_searchState.value == State.SEARCHING || _searchState.value == State.PAUSED) {
            if (totalKeys > 0) {
                _progress.value = ((keysChecked * 100) / totalKeys).toInt()
            }
            val elapsedTimeSec = (System.currentTimeMillis() - searchStartTime) / 1000.0
            if (elapsedTimeSec > 1) { // انتظر ثانية لمعدل ثابت
                val keysPerSecond = keysChecked / elapsedTimeSec
                _statsText.value = String.format(
                    "%.0f مفتاح/ثانية | فحص: %d / %d",
                    keysPerSecond, keysChecked, totalKeys
                )
            }
        }
    }

    // إنهاء البحث
    fun onSearchFinished() {
        if (_searchState.value != State.FOUND) {
            _searchState.value = State.STOPPED
            if (_progress.value != 100) {
                _statsText.value = "تم إيقاف البحث من قبل المستخدم."
            } else {
                _statsText.value = "اكتمل البحث ولم يتم إيجاد المفتاح."
            }
        }
    }
}
