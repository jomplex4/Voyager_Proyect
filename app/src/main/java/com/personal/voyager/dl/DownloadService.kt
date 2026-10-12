package com.personal.voyager.dl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.personal.voyager.MainActivity
import com.personal.voyager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive while the screen is off. Android requires a notification for this kind of service, so
 * it uses a blocked channel: nothing is shown in the status bar or the notification shade.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel_downloads), NotificationManager.IMPORTANCE_NONE)
        )
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voyager:downloads")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, build(Downloads.jobs.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (collector == null) {
            collector = scope.launch {
                Downloads.jobs.collect { list ->
                    if (list.none { it.status == JobStatus.QUEUED || it.running }) {
                        finish()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(ID, build(list))
                        if (wake?.isHeld == false) wake?.acquire(6L * 60 * 60 * 1000)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        runCatching { if (wake?.isHeld == true) wake?.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15 limits dataSync services to about 6 hours per day: pause cleanly instead of crashing. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Downloads.pauseAll()
        finish()
    }

    override fun onDestroy() {
        runCatching { if (wake?.isHeld == true) wake?.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun build(list: List<DlJob>): Notification {
        val active = list.filter { it.running }
        val processing = active.any { it.status == JobStatus.PROCESSING }
        val pct = if (active.isEmpty()) 0 else (active.map { it.progress }.average() * 100).toInt()
        val waiting = list.count { it.status == JobStatus.QUEUED }
        val text = when {
            active.isEmpty() -> "Waiting to start…"
            processing && active.all { it.status == JobStatus.PROCESSING } -> "Processing…"
            else -> "Downloading ${active.size + waiting} file(s) · $pct%"
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, "play"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("VOYAGER")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, pct, active.isEmpty() || processing)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 1001
    }
}
