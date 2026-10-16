package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.file.FileManager

class TransferForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "quick_transfer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_TRANSFER = "com.example.service.START_TRANSFER"
        const val ACTION_UPDATE_PROGRESS = "com.example.service.UPDATE_PROGRESS"
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_SERVICE"

        const val EXTRA_PROGRESS_PERCENT = "progress_percent"
        const val EXTRA_SPEED_TEXT = "speed_text"
        const val EXTRA_FILE_NAME = "file_name"

        fun startService(context: Context, fileName: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START_TRANSFER
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, percent: Int, speedText: String, fileName: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_PROGRESS_PERCENT, percent)
                putExtra(EXTRA_SPEED_TEXT, speedText)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRANSFER -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File"
                startForeground(NOTIFICATION_ID, buildNotification(0, "Connecting...", fileName))
            }
            ACTION_UPDATE_PROGRESS -> {
                val percent = intent.getIntExtra(EXTRA_PROGRESS_PERCENT, 0)
                val speed = intent.getStringExtra(EXTRA_SPEED_TEXT) ?: ""
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File"
                val notification = buildNotification(percent, speed, fileName)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(percent: Int, speed: String, fileName: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val contentText = if (speed.isNotBlank()) "$percent% • $speed" else "$percent%"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quick Transfer: $fileName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live file transfer progress and status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
