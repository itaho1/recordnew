package com.example.screenrecorder.service

import android.content.Intent
import android.service.quicksettings.TileService
import android.service.quicksettings.Tile
import android.app.PendingIntent
import android.os.Build
import com.example.screenrecorder.MainActivity
import com.example.screenrecorder.R

class QuickSettingsTileService : TileService() {
    
    private var isRecording = false
    
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }
    
    override fun onClick() {
        super.onClick()
        
        // פתיחת האפליקציה בלחיצה על האייקון
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, intent, 0)
        }
        
        startActivityAndCollapse(pendingIntent)
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
} 