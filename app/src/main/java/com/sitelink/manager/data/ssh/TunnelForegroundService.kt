package com.sitelink.manager.data.ssh

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
import com.sitelink.manager.MainActivity
import com.sitelink.manager.SiteLinkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TunnelForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            val app = application as? SiteLinkApp
            app?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    it.sshRepository.closeAllTunnels()
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("SiteLink Active: SSH tunnels connected")
        startForeground(NOTIFICATION_ID, notification)

        // Observe active tunnels and auto-stop when 0
        val app = application as? SiteLinkApp
        app?.let {
            serviceScope.launch {
                it.sshRepository.activeTunnels.collectLatest { tunnels ->
                    if (tunnels.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val tunnelDescriptions = tunnels.values.joinToString(", ") { t ->
                            "${t.siteName} (:${t.remotePort} -> :${t.localPort})"
                        }
                        val updatedNotif = buildNotification(tunnelDescriptions)
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, updatedNotif)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, TunnelForegroundService::class.java).apply {
            action = ACTION_DISCONNECT_ALL
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SiteLink Manager — Active SSH Tunnels")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect All", disconnectPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SiteLink Active Tunnels",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SSH port forwarding tunnels alive while using browser"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "sitelink_tunnels_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT_ALL = "com.sitelink.manager.ACTION_DISCONNECT_ALL"

        fun startService(context: Context) {
            val intent = Intent(context, TunnelForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TunnelForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
