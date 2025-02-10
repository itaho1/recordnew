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
import android.os.Handler
import android.os.Looper
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
import com.example.screenrecorder.service.QuickSettingsTileService
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
                    
                    // יציאה למסך הבית רק אחרי שההקלטה התחילה
                    if (intent.getBooleanExtra("from_tile", false)) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                        }, 200)
                    }
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
            
            // יציאה למסך הבית אחרי התחלת ההקלטה
            Handler(Looper.getMainLooper()).postDelayed({
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }, 500)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Settings.canDrawOverlays(this)) {
            checkAndRequestPermissions(getRequiredPermissions())
        } else {
            Toast.makeText(this, "נדרשת הרשאה להצגת חלון צף", Toast.LENGTH_SHORT).show()
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "onCreate called with intent action: ${intent?.action}")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        registerReceiver()
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with action: ${intent?.action}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "Handling intent with action: ${intent?.action}")
        if (intent?.action == "START_RECORDING_FROM_TILE") {
            Log.d(TAG, "Starting recording from tile")
            checkPermissions()
        }
    }

    private fun setupClickListeners() {
        binding.recordButton.setOnClickListener {
            if (!isRecording) {
                checkPermissions()
            } else {
                stopRecording()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        permissions.addAll(getRequiredPermissions())

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        checkAndRequestPermissions(permissions)
    }

    private fun checkAndRequestPermissions(permissions: List<String>) {
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
            updateQuickSettingsTile(false)
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
            // העבר את התוצאה ל-QuickSettingsTileService
            val tileIntent = Intent(this, QuickSettingsTileService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startService(tileIntent)
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

    private fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // אנדרואיד 11 ומעלה
            listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            // אנדרואיד 10 ומטה
            listOf(
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

    private fun updateQuickSettingsTile(isRecording: Boolean) {
        val intent = Intent(this, QuickSettingsTileService::class.java)
        intent.action = "UPDATE_RECORDING_STATE"
        intent.putExtra("isRecording", isRecording)
        sendBroadcast(intent)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 123
        private const val SCREEN_RECORD_REQUEST_CODE = 100
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1234
        private const val TAG = "MainActivity"
    }
}