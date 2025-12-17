package com.example.senioroslauncher.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.senioroslauncher.R
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.guardian.AlertManager
import com.example.senioroslauncher.ui.emergency.EmergencyActivity
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // Fall detection parameters
    private val FALL_THRESHOLD = 2.0f  // Free fall threshold (g)
    private val IMPACT_THRESHOLD = 35.0f  // Impact threshold (g)
    private val FALL_WINDOW = 300L  // Time window for fall detection (ms)

    private var potentialFallTime: Long = 0
    private var inFreeFall = false

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startAccelerometerMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    private fun startAccelerometerMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val curTime = System.currentTimeMillis()
        if ((curTime - lastUpdate) < 20) return // Limit to ~50Hz

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total acceleration magnitude
        val acceleration = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        // Free fall detection (acceleration close to 0)
        if (acceleration < FALL_THRESHOLD && !inFreeFall) {
            inFreeFall = true
            potentialFallTime = curTime
        }

        // Impact detection after free fall
        if (inFreeFall && acceleration > IMPACT_THRESHOLD) {
            val timeSinceFreeFall = curTime - potentialFallTime
            if (timeSinceFreeFall < FALL_WINDOW) {
                // Fall detected!
                onFallDetected()
            }
            inFreeFall = false
        }

        // Reset free fall state after window expires
        if (inFreeFall && (curTime - potentialFallTime) > FALL_WINDOW) {
            inFreeFall = false
        }

        // Sudden acceleration change detection
        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ
        val deltaAccel = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / SensorManager.GRAVITY_EARTH

        if (deltaAccel > 25) {
            // Possible fall from sudden movement
            onPossibleFall()
        }

        lastX = x
        lastY = y
        lastZ = z
        lastUpdate = curTime
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun onFallDetected() {
        // Vibrate to alert user
        vibrate()

        // Show notification
        showFallNotification()

        // Trigger Guardian alert
        AlertManager.triggerFallAlert(this)

        // Launch emergency activity
        val intent = Intent(this, EmergencyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("fall_detected", true)
        }
        startActivity(intent)
    }

    private fun onPossibleFall() {
        // Less aggressive - just vibrate
        vibrate()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Distinctive pattern for fall detection
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
        }
    }

    private fun showFallNotification() {
        val intent = Intent(this, EmergencyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val imOkIntent = Intent(this, FallDetectionReceiver::class.java).apply {
            action = ACTION_IM_OK
        }
        val imOkPendingIntent = PendingIntent.getBroadcast(
            this, 0, imOkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SeniorLauncherApp.CHANNEL_FALL_DETECTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fall Detected!")
            .setContentText("Are you okay? Tap to respond or emergency will be called.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .addAction(0, "I'm OK", imOkPendingIntent)
            .addAction(0, "Get Help", pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(FALL_NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, com.example.senioroslauncher.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SeniorLauncherApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fall Detection Active")
            .setContentText("Monitoring for falls to keep you safe")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val FALL_NOTIFICATION_ID = 1002
        const val ACTION_IM_OK = "com.example.senioroslauncher.IM_OK"
    }
}

// Simple receiver for "I'm OK" action
class FallDetectionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == FallDetectionService.ACTION_IM_OK) {
            // Cancel the fall notification
            context?.let {
                val notificationManager = it.getSystemService(NotificationManager::class.java)
                notificationManager.cancel(1002)
            }
        }
    }
}
