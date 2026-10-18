package com.example.cutline.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.cutline.MainActivity
import com.example.cutline.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExportService : Service() {
    @Inject lateinit var exportController: ExportController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var thermalJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification(0))
        thermalJob = scope.launch {
            val power = getSystemService(PowerManager::class.java)
            while (exportController.snapshot.value.status == ExportStatus.EXPORTING) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    power?.currentThermalStatus == PowerManager.THERMAL_STATUS_SEVERE
                ) {
                    exportController.cancel()
                }
                kotlinx.coroutines.delay(2_000)
            }
        }
        scope.launch {
            exportController.snapshot.collectLatest { state ->
                when (state.status) {
                    ExportStatus.EXPORTING -> update(state.progress)
                    ExportStatus.COMPLETE,
                    ExportStatus.FAILED,
                    ExportStatus.CANCELLED -> {
                        update(state.progress)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    ExportStatus.IDLE -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) exportController.cancel()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        thermalJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun update(progress: Int) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification(progress))
    }

    private fun notification(progress: Int): Notification {
        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, ExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Exporting video")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Video exports", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "exports"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.example.cutline.CANCEL_EXPORT"
    }
}
