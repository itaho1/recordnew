package com.example.screenrecorder.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.screenrecorder.databinding.ActivityMainBinding
import com.example.screenrecorder.service.RecordingService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    
    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startScreenRecording()
        } else {
            Toast.makeText(this, "נדרשות הרשאות להקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    private val startMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val service = Intent(this, RecordingService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service)
            } else {
                startService(service)
            }
            isRecording = true
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.recordButton.setOnClickListener {
            if (!isRecording) {
                checkAndRequestPermissions()
            } else {
                stopRecording()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isEmpty()) {
            startScreenRecording()
        } else {
            requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
        }
    }

    private fun startScreenRecording() {
        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun stopRecording() {
        Intent(this, RecordingService::class.java).also { stopService(it) }
        isRecording = false
        updateUI()
    }

    private fun updateUI() {
        binding.recordButton.text = if (isRecording) "הפסק הקלטה" else "התחל הקלטה"
    }
} 