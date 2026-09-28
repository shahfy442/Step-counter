package com.rshah.steps

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class StepService : Service(), SensorEventListener {
    private val detector = StepDetector { StepRepo.add(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sm: SensorManager
    private var wl: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        StepRepo.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Step counting", NotificationManager.IMPORTANCE_LOW)
        )
        ServiceCompat.startForeground(
            this, ID, notif(StepRepo.steps.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        )

        // Keep the CPU awake so the accelerometer keeps delivering with the screen off.
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "steps:counter").also { it.acquire() }

        sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        StepRepo.running.value = true

        scope.launch { StepRepo.sensitivity.collect { detector.sensitivity = it } }
        scope.launch { StepRepo.steps.collect { nm.notify(ID, notif(it)) } }
    }

    override fun onSensorChanged(e: SensorEvent) =
        detector.onSample(e.values[0], e.values[1], e.values[2], e.timestamp)

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        sm.unregisterListener(this)
        wl?.takeIf { it.isHeld }?.release()
        scope.cancel()
        StepRepo.running.value = false
        super.onDestroy()
    }

    private fun notif(n: Int): Notification =
        NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("$n steps today")
            .setContentText("Counting in the background")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private companion object { const val CH = "steps"; const val ID = 1 }
}
