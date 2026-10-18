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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps downloads alive while the screen is off. Android requires a notification for this kind of service, so it
 * uses a blocked channel (nothing is shown) and it is posted ONCE per change in the number of files, never per
 * progress tick: updating it several times a second makes the system UI work non-stop.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        // Channels keep the settings of the first time they were created, so version 1.0.0's visible channel is
        // removed and a new blocked one is used.
        runCatching { nm.deleteNotificationChannel(OLD_CHANNEL) }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel_downloads), NotificationManager.IMPORTANCE_NONE)
        )
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voyager:downloads")
    }

    private fun pending(list: List<DlJob>) = list.count { it.status == JobStatus.QUEUED || it.running }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, build(pending(Downloads.jobs.value)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (collector == null) {
            collector = scope.launch {
                Downloads.jobs.map { pending(it) }.distinctUntilChanged().collect { count ->
                    if (count == 0) {
                        finish()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(ID, build(count))
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

    private fun build(count: Int): Notification {
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
            .setContentText(if (count <= 1) "Downloading" else "Downloading $count files")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val OLD_CHANNEL = "downloads"
        private const val CHANNEL = "downloads_silent"
        private const val ID = 1001
    }
}
