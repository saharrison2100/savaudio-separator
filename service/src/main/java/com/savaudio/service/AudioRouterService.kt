package com.savaudio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.savaudio.core.OutputDevice
import com.savaudio.core.RoutePlanner
import com.savaudio.core.RouteRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps one [RouteSession] per routed app and re-plans
 * whenever the rules or the connected outputs change.
 */
class AudioRouterService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var monitor: DeviceMonitor
    private lateinit var rules: RuleRepository
    private var projection: MediaProjection? = null
    private var planJob: Job? = null
    private var routedUsage = android.media.AudioAttributes.USAGE_MEDIA
    private val sessions = LinkedHashMap<String, RouteSession>()
    private val sessionDeviceNames = HashMap<String, String>()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() = stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        monitor = DeviceMonitor(this)
        rules = RuleRepository.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data == null || projection != null) {
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        // The projection may only be obtained once the service is in the foreground.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(emptyMap()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = try { manager.getMediaProjection(code, data) } catch (_: Exception) { null }
        if (granted == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        granted.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = granted
        routedUsage = VolumeControl.usage(RouterSettings.get(this).state.value)
        VolumeControl.applyOnStart(this)
        monitor.start()
        planJob = scope.launch {
            combine(rules.rules, monitor.devices) { r, d -> r to d }.collect { (r, d) -> reconcile(r, d) }
        }
        return START_NOT_STICKY
    }

    private fun reconcile(ruleList: List<RouteRule>, devices: List<OutputDevice>) {
        val projection = projection ?: return
        val plan = RoutePlanner.plan(ruleList, devices)
        val errors = HashMap<String, String>()

        // Unpin routes whose rule is gone, whose output was lost, or whose target changed.
        val stale = sessions.filter { (pkg, session) -> plan[pkg]?.id != session.deviceId }.keys.toList()
        for (pkg in stale) {
            sessions.remove(pkg)?.stop()
            sessionDeviceNames.remove(pkg)
        }

        for ((pkg, device) in plan) {
            if (sessions.containsKey(pkg)) continue
            try {
                val uid = packageManager.getApplicationInfo(pkg, 0).uid
                val info = monitor.find(device.id) ?: throw IllegalStateException("Output is no longer connected")
                val session = RouteSession(pkg, uid, projection, device.id, info, routedUsage)
                session.start()
                sessions[pkg] = session
                sessionDeviceNames[pkg] = device.displayName
            } catch (e: Exception) {
                errors[pkg] = e.message ?: "Could not start this route"
            }
        }
        publish(errors)
    }

    private fun publish(errors: Map<String, String>) {
        val active = HashMap(sessionDeviceNames)
        RouterState.set(RouterStatus(running = true, active = active, errors = errors))
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(active))
    }

    private fun buildNotification(active: Map<String, String>): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.router_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AudioRouterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = if (active.isEmpty()) getString(R.string.router_notification_idle)
        else active.entries.joinToString(", ") { (pkg, device) -> "${appLabel(pkg)} to $device" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(getString(R.string.router_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launch)
            .addAction(0, getString(R.string.router_notification_stop), stop)
            .build()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    override fun onDestroy() {
        planJob?.cancel()
        scope.cancel()
        sessions.values.forEach { it.stop() }
        sessions.clear()
        sessionDeviceNames.clear()
        try { monitor.stop() } catch (_: Exception) {}
        projection?.let {
            try { it.unregisterCallback(projectionCallback) } catch (_: Exception) {}
            try { it.stop() } catch (_: Exception) {}
        }
        projection = null
        VolumeControl.restore(this)
        RouterState.set(RouterStatus())
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.savaudio.service.STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "routing"
        private const val NOTIFICATION_ID = 41

        /** [resultCode] and [data] come from the system screen-capture consent dialog. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, AudioRouterService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioRouterService::class.java))
        }
    }
}
