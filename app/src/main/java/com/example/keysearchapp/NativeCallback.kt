package com.example.keysearchapp

interface NativeCallback {
    fun onKeyFound(key: String)
    fun onProgressUpdate(keysChecked: Long)
    fun onSearchFinished()
}