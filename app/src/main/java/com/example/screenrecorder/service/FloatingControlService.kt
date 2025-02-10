package com.example.screenrecorder.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.ViewFlipper
import android.os.Handler
import android.os.Looper
import com.example.screenrecorder.R
import android.widget.Toast
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.screenrecorder.MainActivity
import android.util.Log

class FloatingControlService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var timerTextView: TextView
    private var startTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        setupFloatingWindow()
    }
    
    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_control, null)
        viewFlipper = floatingView.findViewById(R.id.viewFlipper)
        timerTextView = floatingView.findViewById(R.id.timerTextView)
        
        setupDragListener()
        setupButtons()
        
        windowManager.addView(floatingView, createWindowParams())
    }
    
    private fun setupButtons() {
        // כפתור עצירה במצב הקלטה
        floatingView.findViewById<View>(R.id.stopButton).setOnClickListener {
            // שליחת broadcast לעצירת ההקלטה
            sendBroadcast(Intent("STOP_RECORDING_FROM_FLOATING_BUTTON").apply {
                setPackage(packageName)
            })
            
            // מעבר למצב בקרים
            viewFlipper.displayedChild = 1
            handler.removeCallbacksAndMessages(null)
        }
        
        // כפתור הקלטה במצב בקרים
        floatingView.findViewById<View>(R.id.recordButton).setOnClickListener {
            startNewRecording()
        }
        
        // כפתור ניגון הקלטה אחרונה במצב בקרים
        floatingView.findViewById<View>(R.id.playLastButton).setOnClickListener {
            playLastRecording()
        }

        // כפתור סגירה במצב בקרים
        floatingView.findViewById<View>(R.id.closeButton).setOnClickListener {
            // סגירת הפאנל
            stopSelf()
        }
    }
    
    private fun startNewRecording() {
        // שליחת broadcast להתחלת הקלטה חדשה
        sendBroadcast(Intent("START_RECORDING").apply {
            setPackage(packageName)
        })
    }
    
    private fun openRecordingsFolder() {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder")
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(folder), "resource/folder")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    setDataAndType(Uri.fromFile(folder), "*/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "לא ניתן לפתוח את תיקיית ההקלטות", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun playLastRecording() {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder")
        val files = folder.listFiles()?.filter { it.name.endsWith(".mp4") }
        val lastFile = files?.maxByOrNull { it.lastModified() }
        
        lastFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "לא ניתן לפתוח את הקובץ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } ?: Toast.makeText(this, "לא נמצאו הקלטות", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var params = createWindowParams()

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // שמירת המיקום ההתחלתי
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // חישוב המיקום החדש
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    
                    // עדכון המיקום
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun createWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }
    
    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() - startTime
                val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60
                val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime)
                timerTextView.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized && ::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_RECORDING_SERVICE" -> {
                // ההקלטה התחילה בהצלחה, נעביר את הפאנל למצב הקלטה
                viewFlipper.displayedChild = 0 // מצב הקלטה עם טיימר
                startTime = System.currentTimeMillis()
                startTimer()
            }
            "STOP_RECORDING_SERVICE" -> {
                // ההקלטה הופסקה
                viewFlipper.displayedChild = 1 // מצב בקרים
                handler.removeCallbacksAndMessages(null)
            }
            else -> {
                // התחלה ראשונית של הפאנל - נתחיל במצב בקרים
                viewFlipper.displayedChild = 1
            }
        }
        return START_STICKY
    }

    companion object {
        private const val TAG = "FloatingControlService"
    }
} 