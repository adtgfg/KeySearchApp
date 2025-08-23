package com.example.keysearchapp

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class SearchService : Service() {

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val start = intent.getLongExtra("start", 0L)
                val end = intent.getLongExtra("end", 1000000L)
                val target = intent.getStringExtra("target") ?: ""
                createNotification()
                startSearchNative(start, end, target, CallbackImpl())
            }
            "PAUSE" -> pauseSearchNative()
            "RESUME" -> resumeSearchNative()
            "STOP" -> stopSearchNative()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification() {
        val channelId = "search_channel"
        val channel = NotificationChannel(
            channelId,
            "Key Search",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("جاري البحث عن المفتاح")
            .setContentText("يمكنك متابعة التقدم من التطبيق")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    // Callback يمرر النتائج إلى MainActivity عبر Broadcast
    inner class CallbackImpl {
        fun onKeyFound(key: String) {
            sendUpdate(foundKey = key)
        }

        fun onProgressUpdate(progress: Long) {
            sendUpdate(progress = progress)
        }

        fun onSearchFinished() {
            sendUpdate(finished = true)
            stopForeground(true)
            stopSelf()
        }
    }

    private fun sendUpdate(
        progress: Long = -1,
        foundKey: String? = null,
        finished: Boolean = false
    ) {
        val intent = Intent("SEARCH_UPDATE").apply {
            if (progress >= 0) putExtra("progress", progress)
            if (foundKey != null) putExtra("foundKey", foundKey)
            putExtra("finished", finished)
        }
        sendBroadcast(intent)
    }

    external fun startSearchNative(start: Long, end: Long, target: String, callback: Any)
    external fun pauseSearchNative()
    external fun resumeSearchNative()
    external fun stopSearchNative()
}