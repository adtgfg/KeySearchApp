package com.example.keysearchapp

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SearchService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val start = intent?.getLongExtra("start", 0L) ?: 0L
        val end = intent?.getLongExtra("end", 0L) ?: 1000000L
        val target = intent?.getStringExtra("target") ?: ""
        // عرض Notification دائم
        createNotification()
        // استدعاء البحث عبر JNI
        startSearchNative(start, end, target, this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotification() {
        val channelId = "search_channel"
        val channel = NotificationChannel(channelId, "Key Search", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("جاري البحث عن المفتاح")
            .setContentText("يمكنك متابعة التقدم من التطبيق")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    external fun startSearchNative(start: Long, end: Long, target: String, callback: Any)
    external fun pauseSearchNative()
    external fun resumeSearchNative()
    external fun stopSearchNative()
}
