package com.example.screenrecorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.screenrecorder.MainActivity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import android.util.Log

class RecordingService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentVideoFile: File? = null

    private val SCREEN_WIDTH = 1080
    private val SCREEN_HEIGHT = 1920
    private val SCREEN_DPI = 1

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "screen_recording_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "RecordingService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRecording) {
            Log.d(TAG, "Recording already in progress, ignoring start command")
            return START_NOT_STICKY
        }
        
        Log.d(TAG, "onStartCommand called with intent: $intent")
        
        try {
            // בדיקת null
            if (intent == null) {
                Log.e(TAG, "Intent is null")
                stopSelf()
                return START_NOT_STICKY
            }

            val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("data")
            }
            
            Log.d(TAG, "Received resultCode: $resultCode")
            Log.d(TAG, "Received data: $data")

            if (resultCode != Activity.RESULT_OK || data == null) {
                Log.e(TAG, "Invalid resultCode ($resultCode) or missing data")
                stopSelf()
                return START_NOT_STICKY
            }

            createNotificationChannel()
            Log.d(TAG, "Created notification channel")
            
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Started foreground service")

            setupMediaProjection(resultCode, data)
            Log.d(TAG, "Created MediaProjection: $mediaProjection")
            
            startRecording()
            
            // שליחת broadcast על התחלת הקלטה
            Intent("RECORDING_STARTED").apply {
                setPackage(packageName)
            }.also { intent ->
                sendBroadcast(intent)
            }
            
            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("הקלטת מסך")
            .setContentText("מקליט כעת...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun startRecording() {
        try {
            Log.d(TAG, "Starting recording process...")
            
            // הגדרת ה-MediaRecorder
            mediaRecorder = setupMediaRecorder()
            Log.d(TAG, "MediaRecorder setup completed")
            
            // חשוב - קודם נרשום את ה-callback
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stopRecording()
                }
            }, null)
            
            // רק אחרי זה ניצור את ה-VirtualDisplay
            Log.d(TAG, "Creating virtual display...")
            createVirtualDisplay()
            
            // התחלת ההקלטה
            mediaRecorder?.start()
            Log.d(TAG, "Recording started successfully")
            isRecording = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopSelf()
            throw e
        }
    }

    private fun setupMediaRecorder(): MediaRecorder {
        val folder = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES), "ScreenRecorder")
        
        try {
            // וודא שהתיקייה קיימת
            if (!folder.exists()) {
                val success = folder.mkdirs()
                if (!success) {
                    throw IOException("Failed to create directory: ${folder.absolutePath}")
                }
            }
            
            // בדוק הרשאות כתיבה
            if (!folder.canWrite()) {
                throw IOException("No write permission for directory: ${folder.absolutePath}")
            }
            
            val filename = "screen_record_${System.currentTimeMillis()}.mp4"
            val file = File(folder, filename)
            
            // נסה ליצור קובץ ריק
            if (!file.createNewFile()) {
                throw IOException("Failed to create file: ${file.absolutePath}")
            }
            
            currentVideoFile = file
            Log.d(TAG, "File created successfully at: ${file.absolutePath}")
            
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                
                Log.d(TAG, "Saving recording to: ${file.absolutePath}")
                
                setOutputFile(file.absolutePath)
                val metrics = resources.displayMetrics
                setVideoSize(metrics.widthPixels, metrics.heightPixels)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(5 * 1024 * 1024)
                setVideoFrameRate(30)
                
                try {
                    prepare()
                    Log.d(TAG, "MediaRecorder prepared successfully")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare MediaRecorder", e)
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupMediaRecorder", e)
            throw e
        }
    }

    private fun createVirtualDisplay() {
        val metrics = resources.displayMetrics
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        ) ?: run {
            val error = "Failed to create virtual display"
            Log.e(TAG, error)
            throw IllegalStateException(error)
        }
        Log.d(TAG, "Virtual display created successfully")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service being destroyed")
        if (isRecording) {
            Log.d(TAG, "Stopping recording from onDestroy")
            stopRecording()
        }
        super.onDestroy()
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                // עצירה מסודרת של ההקלטה
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            
            // שחרור המשאבים של המסך
            virtualDisplay?.release()
            virtualDisplay = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            // שליחת broadcast על סיום הקלטה
            Intent("RECORDING_COMPLETED").apply {
                setPackage(packageName)
                putExtra("file_path", currentVideoFile?.absolutePath)
            }.also { broadcastIntent ->
                sendBroadcast(broadcastIntent)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            // וידוא שכל המשאבים משוחררים
            try {
                mediaRecorder?.release()
                virtualDisplay?.release()
                mediaProjection?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing resources", e)
            }
            
            mediaRecorder = null
            virtualDisplay = null
            mediaProjection = null
        }
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)?.also {
            Log.d(TAG, "MediaProjection created successfully")
        } ?: run {
            val error = "Failed to create MediaProjection"
            Log.e(TAG, error)
            throw IllegalStateException(error)
        }
    }
} 