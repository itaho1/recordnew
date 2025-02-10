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
import android.os.Handler
import android.os.Looper
import com.example.screenrecorder.R
import java.util.concurrent.TimeUnit

class FloatingControlService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var timerTextView: TextView
    private var startTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // יצירת החלון הצף
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_control, null)
        timerTextView = floatingView.findViewById(R.id.timerTextView)
        
        // הגדרת פרמטרים לחלון הצף
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        
        // הוספת כפתור העצירה
        floatingView.findViewById<View>(R.id.stopButton).setOnClickListener {
            sendBroadcast(
                Intent("STOP_RECORDING_FROM_FLOATING_BUTTON")
                    .setPackage(packageName)
            )
            stopSelf()
        }
        
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
        
        windowManager.addView(floatingView, params)
        startTime = System.currentTimeMillis()
        startTimer()
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
            windowManager.removeView(floatingView)
        }
        handler.removeCallbacksAndMessages(null)
    }
} 