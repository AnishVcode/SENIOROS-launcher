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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.senioroslauncher.R
import com.example.senioroslauncher.SeniorLauncherApp
import com.example.senioroslauncher.data.guardian.AlertManager
import com.example.senioroslauncher.ui.emergency.EmergencyActivity
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "FallDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val FALL_NOTIFICATION_ID = 1002
        const val ACTION_IM_OK = "com.example.senioroslauncher.IM_OK"
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // Fall detection parameters
    private val FALL_THRESHOLD = 0.5f  // Free fall threshold (g) - lowered for better detection
    private val IMPACT_THRESHOLD = 2.5f  // Impact threshold (g) - more realistic for phone drops
    private val FALL_WINDOW = 500L  // Time window for fall detection (ms) - increased window

    private var potentialFallTime: Long = 0
    private var inFreeFall = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FallDetectionService onCreate()")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer sensor available!")
        } else {
            Log.d(TAG, "Accelerometer sensor found: ${accelerometer?.name}")
        }
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
            val success = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
            Log.d(TAG, "Accelerometer monitoring started: $success")
        } ?: Log.e(TAG, "Cannot start monitoring - no accelerometer")
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
            Log.d(TAG, "Free fall detected! acceleration=$acceleration")
        }

        // Impact detection after free fall
        if (inFreeFall && acceleration > IMPACT_THRESHOLD) {
            val timeSinceFreeFall = curTime - potentialFallTime
            Log.d(TAG, "Impact detected! acceleration=$acceleration, timeSinceFreeFall=$timeSinceFreeFall ms")
            if (timeSinceFreeFall < FALL_WINDOW) {
                // Fall detected!
                Log.w(TAG, "FALL DETECTED! Triggering emergency alert")
                onFallDetected()
            }
            inFreeFall = false
        }

        // Reset free fall state after window expires
        if (inFreeFall && (curTime - potentialFallTime) > FALL_WINDOW) {
            Log.d(TAG, "Free fall window expired without impact")
            inFreeFall = false
        }

        // Sudden acceleration change detection
        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ
        val deltaAccel = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / SensorManager.GRAVITY_EARTH

        if (deltaAccel > 3.0) {
            // Possible fall from sudden movement - lowered threshold for better detection
            Log.d(TAG, "Sudden movement detected: deltaAccel=$deltaAccel")
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
        Log.w(TAG, "onFallDetected() - Processing fall event")

        // Vibrate to alert user
        vibrate()
        Log.d(TAG, "Vibration triggered")

        // Show notification
        showFallNotification()
        Log.d(TAG, "Fall notification shown")

        // Trigger Guardian alert
        AlertManager.triggerFallAlert(this)
        Log.d(TAG, "Guardian alert triggered")

        // Launch emergency activity
        val intent = Intent(this, EmergencyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("fall_detected", true)
        }
        startActivity(intent)
        Log.d(TAG, "Emergency activity launched")
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
