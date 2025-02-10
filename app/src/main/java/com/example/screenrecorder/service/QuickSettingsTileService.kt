package com.example.screenrecorder.service

import android.content.Intent
import android.service.quicksettings.TileService
import android.service.quicksettings.Tile
import android.media.projection.MediaProjectionManager
import android.app.Activity
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.PendingIntent
import com.example.screenrecorder.R
import com.example.screenrecorder.service.RecordingService
import com.example.screenrecorder.MainActivity
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import android.app.StatusBarManager
import android.os.Handler
import android.os.Looper
import android.content.ComponentName

class QuickSettingsTileService : TileService() {
    
    private var isRecording = false
    
    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "RECORDING_STARTED" -> {
                    isRecording = true
                    updateTileState()
                }
                "RECORDING_COMPLETED" -> {
                    isRecording = false
                    updateTileState()
                }
            }
        }
    }

    private val statusBarManager by lazy {
        getSystemService(Context.STATUS_BAR_SERVICE) as? StatusBarManager
    }

    init {
        Log.d(TAG, "QuickSettingsTileService initialized")
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added to quick settings")
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Started listening to tile events")
        updateTileState()
    }
    
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked! Current recording state: $isRecording")
        
        if (!isRecording) {
            // סגירת פאנל ההגדרות המהירות
            requestListeningState(applicationContext, ComponentName(applicationContext, QuickSettingsTileService::class.java))
            
            // המתנה קצרה לפני פתיחת האפליקציה
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    action = "START_RECORDING_FROM_TILE"
                }
                startActivity(intent)
                // נעביר את היציאה למסך הבית ל-MainActivity
            }, 200)
        } else {
            stopRecording()
        }
    }
    
    private fun updateTileState() {
        qsTile?.let { tile ->
            tile.state = if (isRecording) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            
            tile.label = getString(
                if (isRecording) R.string.stop_recording 
                else R.string.start_recording
            )
            
            tile.updateTile()
        }
    }
    
    fun updateRecordingState(isRecording: Boolean) {
        this.isRecording = isRecording
        updateTileState()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "QuickSettingsTileService onCreate")
        val filter = IntentFilter().apply {
            addAction("RECORDING_STARTED")
            addAction("RECORDING_COMPLETED")
        }
        ContextCompat.registerReceiver(
            this,
            recordingStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "QuickSettingsTileService onDestroy")
        try {
            unregisterReceiver(recordingStateReceiver)
        } catch (e: Exception) {
            // הרסיבר כבר בוטל
        }
    }

    // נוסיף פונקציה חדשה לטיפול בתוצאת ההרשאה
    fun onScreenCaptureResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, RecordingService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startService(intent)
            isRecording = true
            updateTileState()
        }
    }

    private fun stopRecording() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = "STOP_RECORDING"
        }
        startService(intent)
        Log.d(TAG, "Sent stop recording command")
        isRecording = false
        updateTileState()
    }

    companion object {
        private const val TAG = "QuickSettingsTile"
    }
} 