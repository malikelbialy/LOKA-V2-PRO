package com.loka.viva.platform

import android.content.Context
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface

class CameraController(
    private val context: Context,
    private val onHandFrame: (ByteArray, Int, Int) -> Unit = { _, _, _ -> }
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraThread: HandlerThread? = null
    private var handler: Handler? = null
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var cameraId = ""

    fun start() {
        cameraThread = HandlerThread("VivaCamera").apply { start() }
        handler = Handler(cameraThread!!.looper)
        openCamera()
    }

    private fun openCamera() {
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = chooseSize(map?.getOutputSizes(ImageFormat.YUV_420_888))
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                    try {
                        val p0 = img.planes[0]; val p1 = img.planes[1]; val p2 = img.planes[2]
                        val ySize = p0.buffer.remaining(); val uv = p1.buffer.remaining() / 2
                        val nv21 = ByteArray(ySize + uv * 2)
                        p0.buffer.get(nv21, 0, ySize)
                        p1.buffer.get(nv21, ySize, uv)
                        p2.buffer.get(nv21, ySize + uv, uv)
                        onHandFrame(nv21, img.width, img.height)
                    } catch (e: Exception) { e.printStackTrace() } finally { img.close() }
                }, handler)
            }
            cameraManager.openCamera(cameraId, stateCallback, handler)
        } catch (e: CameraAccessException) { e.printStackTrace() }
    }

    private fun chooseSize(sizes: Array<Size>?): Size {
        if (sizes == null || sizes.isEmpty()) return Size(640, 480)
        return sizes.filter { it.width >= 640 && it.height >= 480 }
            .minByOrNull { it.width.toLong() * it.height } ?: sizes[0]
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createSession()
        }
        override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
        override fun onError(camera: CameraDevice, error: Int) { camera.close(); cameraDevice = null }
    }

    private fun createSession() {
        val dev = cameraDevice ?: return
        val targets = mutableListOf<Surface>()
        previewSurface?.let { targets.add(it) }
        imageReader?.surface?.let { targets.add(it) }
        if (targets.isEmpty()) return
        try {
            @Suppress("DEPRECATION")
            dev.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cfg: CameraCaptureSession) {
                    session = cfg
                    val b = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    targets.forEach { b.addTarget(it) }
                    cfg.setRepeatingRequest(b.build(), null, handler)
                }
                override fun onConfigureFailed(cfg: CameraCaptureSession) {}
            }, handler)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setPreviewSurface(surface: Surface) {
        previewSurface = surface
    }

    fun pause() {
        try { session?.close(); session = null; cameraDevice?.close(); cameraDevice = null }
        catch (e: Exception) { e.printStackTrace() }
    }

    fun stop() {
        pause()
        imageReader?.close(); imageReader = null
        cameraThread?.quitSafely(); cameraThread = null; handler = null
    }
}
