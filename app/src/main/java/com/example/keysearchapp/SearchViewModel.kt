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

    fun startSearch(totalKeysInRange: Long) {
        totalKeys = totalKeysInRange
        searchStartTime = System.currentTimeMillis()
        _searchState.value = State.SEARCHING
        _progress.value = 0
        _statsText.value = "Starting up..."
        _foundKey.value = null
    }

    fun forceStopState(){
        _searchState.value = State.STOPPED
    }

    fun onKeyFound(key: String) {
        _searchState.value = State.FOUND
        _foundKey.value = key
        _progress.value = 100
    }

    fun onProgressUpdate(keysChecked: Long) {
        if (_searchState.value == State.SEARCHING || _searchState.value == State.PAUSED) {
            if (totalKeys > 0) {
                _progress.value = ((keysChecked * 100) / totalKeys).toInt()
            }
            val elapsedTimeSec = (System.currentTimeMillis() - searchStartTime) / 1000.0
            if (elapsedTimeSec > 1) { // Wait a second for a stable rate
                val keysPerSecond = keysChecked / elapsedTimeSec
                _statsText.value = String.format(
                    "%.0f مفتاح/ثانية | فحص: %d / %d",
                    keysPerSecond, keysChecked, totalKeys
                )
            }
        }
    }

    fun setPaused(isPaused: Boolean){
        _searchState.value = if(isPaused) State.PAUSED else State.SEARCHING
    }

    fun onSearchFinished() {
        if (_searchState.value != State.FOUND) {
            _searchState.value = State.STOPPED
            if (_progress.value != 100) {
                 _statsText.value = "Search stopped by user."
            } else {
                 _statsText.value = "Search completed. Key not found in range."
            }
        }
    }
}
