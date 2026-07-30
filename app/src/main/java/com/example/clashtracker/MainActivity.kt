package com.example.clashtracker

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                statusText.text = "Overlay running — switch to Clash Royale"
            } else {
                statusText.text = "Screen capture permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val startButton = findViewById<Button>(R.id.startButton)

        startButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                statusText.text = "Grant the overlay permission, then tap the button again"
                return@setOnClickListener
            }

            if (!ForegroundAppChecker.hasUsageAccess(this)) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                statusText.text =
                    "Find \"Clash Tracker\" in the list and enable usage access, " +
                    "then come back and tap the button again. This is what lets " +
                    "the app tell when Clash Royale is actually open, so it never " +
                    "runs anywhere else."
                return@setOnClickListener
            }

            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
