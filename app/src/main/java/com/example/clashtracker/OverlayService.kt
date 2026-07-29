package com.example.clashtracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.opencv.android.OpenCVLoader

class OverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val NOTIFICATION_CHANNEL_ID = "clash_tracker_channel"
        const val NOTIFICATION_ID = 1001

        // TODO: calibrate this per your device's resolution — this is the
        // region of the screen where the opponent's "just played" card
        // slot appears. Placeholder values assume a 1080x2400-ish portrait
        // capture; adjust after checking captured frames on your device.
        val CAPTURE_REGION = Rect(400, 600, 700, 900)

        const val PROCESS_INTERVAL_MS = 350L // ~2-3 fps
        const val UI_TICK_MS = 250L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var elixirText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var lastPlayedText: TextView
    private lateinit var predictedText: TextView
    private lateinit var overlayParams: WindowManager.LayoutParams

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastProcessedMs = 0L
    private var overlayLocked = true

    private val uiHandler = Handler(Looper.getMainLooper())

    private val elixirTracker = ElixirTracker()
    private val cycleTracker = CycleTracker()
    private lateinit var cardDetector: CardDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initLocal()) {
            // OpenCV failed to load — detection will no-op. Check the
            // OpenCV Android SDK setup instructions in the README.
        }
        CardDatabase.load(this)
        cardDetector = CardDetector(this)
        cardDetector.loadTemplates()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
        elixirTracker.startMatch()
        startUiTickLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ClashTrackerCapture",
            width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastProcessedMs < PROCESS_INTERVAL_MS) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastProcessedMs = now

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image, width, height)
                val cropped = cropSafe(bitmap, CAPTURE_REGION)
                processFrame(cropped)
            } finally {
                image.close()
            }
        }, uiHandler)
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun cropSafe(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height)
        )
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }

    private fun processFrame(region: Bitmap) {
        val detection = cardDetector.detect(region) ?: return
        val (cardName, confidence) = detection
        elixirTracker.onCardPlayed(cardName, confidence)
        cycleTracker.onCardPlayed(cardName)
    }

    private fun startUiTickLoop() {
        uiHandler.post(object : Runnable {
            override fun run() {
                elixirTracker.tick()
                updateOverlayText()
                uiHandler.postDelayed(this, UI_TICK_MS)
            }
        })
    }

    private fun updateOverlayText() {
        elixirText.text = "Elixir (est.): %.1f".format(elixirTracker.currentEstimate())
        confidenceText.text = "Confidence: %.0f%%".format(elixirTracker.confidence * 100)
        val last4 = cycleTracker.lastFour()
        lastPlayedText.text = "Last 4: ${if (last4.isEmpty()) "--" else last4.joinToString(", ")}"
        val next = cycleTracker.likelyNext()
        predictedText.text = "Likely next: ${if (next.isEmpty()) "--" else next.joinToString(", ")}"
    }

    private fun createOverlayView() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        elixirText = overlayView.findViewById(R.id.elixirText)
        confidenceText = overlayView.findViewById(R.id.confidenceText)
        lastPlayedText = overlayView.findViewById(R.id.lastPlayedText)
        predictedText = overlayView.findViewById(R.id.predictedText)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.TOP or Gravity.START
        // Small inset from the top-left corner of the screen, clear of
        // notches/status bars. Adjust if your device has a large cutout.
        overlayParams.x = 24
        overlayParams.y = 80

        setupDragHandle()
        windowManager.addView(overlayView, overlayParams)
    }

    /**
     * The overlay is FLAG_NOT_TOUCHABLE by default so it never steals game
     * input. Long-press the drag handle strip to temporarily unlock and
     * reposition; it re-locks automatically when you lift your finger.
     */
    private fun setupDragHandle() {
        val handle = overlayView.findViewById<View>(R.id.dragHandle)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnLongClickListener {
            overlayLocked = false
            overlayParams.flags = overlayParams.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager.updateViewLayout(overlayView, overlayParams)
            true
        }

        handle.setOnTouchListener { _, event ->
            if (overlayLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = overlayParams.x
                    initialY = overlayParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams.x = initialX + (event.rawX - touchX).toInt()
                    overlayParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, overlayParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    overlayLocked = true
                    overlayParams.flags = overlayParams.flags or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    windowManager.updateViewLayout(overlayView, overlayParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        uiHandler.removeCallbacksAndMessages(null)
    }
}
