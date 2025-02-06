package com.example.screenrecorder

import android.app.Activity
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.screenrecorder.databinding.ActivityMainBinding
import com.example.screenrecorder.service.RecordingService
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var receiverRegistered = false
    private var lastRecordedFile: File? = null
    
    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "RECORDING_STARTED" -> {
                    isRecording = true
                    updateUI()
                }
                "RECORDING_COMPLETED" -> {
                    isRecording = false
                    updateUI()
                    intent.getStringExtra("file_path")?.let { path ->
                        lastRecordedFile = File(path)
                    }
                }
            }
        }
    }

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, RecordingService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isRecording = true
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // הגדרת OpenGL
            window.setFormat(PixelFormat.RGBA_8888)
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupClickListeners()
            registerReceiver()

            binding.openFolderButton.setOnClickListener {
                openRecordingsFolder()
            }

            binding.checkFileButton.setOnClickListener {
                checkLastRecording()
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "שגיאה באתחול: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        val permissions = getRequiredPermissions()
        
        if (hasPermissions(permissions)) {
            startScreenRecording()
        } else {
            ActivityCompat.requestPermissions(
                this,
                permissions,
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun startScreenRecording() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startForResult.launch(captureIntent)
    }

    private fun registerReceiver() {
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
        receiverRegistered = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(recordingStateReceiver)
        }
    }

    private fun stopRecording() {
        try {
            val intent = Intent(this, RecordingService::class.java)
            stopService(intent)
            isRecording = false
            updateUI()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            Toast.makeText(this, "שגיאה בעצירת ההקלטה: ${e.message}", 
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        binding.recordButton.text = if (isRecording) {
            getString(R.string.stop_recording)
        } else {
            getString(R.string.start_recording)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // אם כל ההרשאות אושרו, נתחיל את ההקלטה
                startScreenRecording()
            } else {
                Toast.makeText(this, "נדרשות כל ההרשאות כדי להקליט מסך", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    val intent = Intent(this, RecordingService::class.java).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("data", data)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    isRecording = true
                    updateUI()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onActivityResult", e)
                    Toast.makeText(this, "שגיאה בהתחלת השירות: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "הרשאת הקלטת מסך נדחתה", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkLastRecording() {
        lastRecordedFile?.let { file ->
            if (file.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, 
                        "לא ניתן לפתוח את הקובץ: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openRecordingsFolder() {
        try {
            val intent = Intent("android.intent.action.MAIN")
            intent.setClassName(
                "com.mi.android.globalFileexplorer",
                "com.android.fileexplorer.FileExplorerTabActivity"
            )
            intent.putExtra(
                "file_path", 
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)}/ScreenRecorder"
            )
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.type = "video/*"
                startActivity(intent)
            } catch (e: Exception) {
                val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                Toast.makeText(this, 
                    "הקבצים נמצאים ב: ${folder.absolutePath}/ScreenRecorder", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        permissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")
            if (!isGranted) return false
        }
        return true
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // אנדרואיד 11 ומעלה
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            // אנדרואיד 10 ומטה
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun canAccessStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() || 
            (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 123
        private const val SCREEN_RECORD_REQUEST_CODE = 100
        private const val TAG = "MainActivity"
    }
}