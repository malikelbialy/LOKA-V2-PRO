package com.loka.viva

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.loka.viva.logic.AppSettings
import com.loka.viva.logic.BackendProvider
import com.loka.viva.logic.CommandParser
import com.loka.viva.logic.VoiceAssistant
import com.loka.viva.logic.WindowStore
import com.loka.viva.platform.CameraController
import com.loka.viva.platform.HeadTracker
import com.loka.viva.platform.HandTracker
import com.loka.viva.platform.HandState
import com.loka.viva.platform.VrRenderer
import com.loka.viva.platform.VrSurfaceView
import com.loka.viva.platform.VWindow
import com.loka.viva.logic.VWindowGeometry
import com.loka.viva.logic.VWindowPosition
import com.loka.viva.logic.VWindowSize
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var cameraController: CameraController
    private lateinit var headTracker: HeadTracker
    private lateinit var handTracker: HandTracker
    private lateinit var vrRenderer: VrRenderer
    private lateinit var glView: VrSurfaceView
    private lateinit var windowStore: WindowStore
    private lateinit var voiceAssistant: VoiceAssistant
    private val backend = BackendProvider()
    private var cameraActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        glView = findViewById(R.id.gl_surface_view)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 1001
            )
        } else {
            initAll()
        }
    }

    private fun initAll() {
        windowStore = WindowStore(this)
        AppSettings(this).load()

        headTracker = HeadTracker(this) { p, y, r ->
            runOnUiThread { vrRenderer.updateHeadRotation(p, y, r) }
        }
        headTracker.start()

        handTracker = HandTracker(this) { state, _ -> handleHand(state) }

        cameraController = CameraController(this) { nv21, w, h ->
            // hand frames arrive on camera thread; convert + run inference
            handFrame(nv21, w, h)
        }

        vrRenderer = VrRenderer(
            context = this,
            onSurfaceReady = { /* gl ready */ },
            windowGeometryProvider = { currentGeometry() },
            onCameraSurfaceReady = { surface -> cameraController.setPreviewSurface(surface) }
        )
        glView.setVrRenderer(vrRenderer)

        voiceAssistant = VoiceAssistant(
            context = this,
            onCommand = { cmd -> handleCommand(cmd) },
            onState = { /* update status text */ }
        )

        cameraController.start()
        handTracker.start()
    }

    private var lastHandFrame = 0L
    private fun handFrame(nv21: ByteArray, w: Int, h: Int) {
        // throttle to ~15 fps
        val now = System.currentTimeMillis()
        if (now - lastHandFrame < 66) return
        lastHandFrame = now
        val bmp = nv21ToBitmap(nv21, w, h) ?: return
        handTracker.processFrame(bmp)
    }

    private fun nv21ToBitmap(nv21: ByteArray, w: Int, h: Int): android.graphics.Bitmap? {
        return try {
            val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null)
            val out = java.io.ByteArrayOutputStream()
            yuv.compressToJpeg(android.graphics.Rect(0, 0, w, h), 80, out)
            val bytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }

    private fun handleHand(state: HandState) {
        // Could drive selection/drag here.
    }

    private fun currentGeometry(): VWindowGeometry {
        val w = windowStore.windows.firstOrNull()
        return if (w != null) {
            VWindowGeometry(w.position, VWindowSize(w.size, w.size))
        } else {
            VWindowGeometry(VWindowPosition(), VWindowSize())
        }
    }

    private fun handleCommand(cmd: String) {
        val intent = CommandParser.parse(cmd)
        when (intent.action) {
            CommandParser.Action.OPEN_URL -> {
                val w = VWindow(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Web",
                    url = intent.query,
                    position = VWindowPosition(),
                    size = 0.6f
                )
                windowStore.addWindow(w)
                runOnUiThread { vrRenderer.addWindow(w) }
            }
            CommandParser.Action.AI_QUERY, CommandParser.Action.UNKNOWN -> {
                backend.sendSimple(intent.query.ifBlank { cmd },
                    onSuccess = { /* show overlay */ },
                    onError = { /* show error */ })
            }
            else -> { /* other actions */ }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::cameraController.isInitialized && !cameraActive) {
            cameraController.start(); cameraActive = true
        }
        if (::voiceAssistant.isInitialized) voiceAssistant.start()
    }

    override fun onPause() {
        super.onPause()
        if (::cameraController.isInitialized) { cameraController.pause(); cameraActive = false }
        if (::voiceAssistant.isInitialized) voiceAssistant.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::headTracker.isInitialized) headTracker.stop()
        if (::handTracker.isInitialized) handTracker.stop()
        if (::cameraController.isInitialized) cameraController.stop()
        if (::vrRenderer.isInitialized) vrRenderer.stop()
        if (::voiceAssistant.isInitialized) voiceAssistant.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initAll()
        }
    }
}