package com.loka.viva.platform

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.hand_landmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.hand_landmarker.HandLandmarkerOptions
import kotlin.math.sqrt

enum class HandState { IDLE, OPEN, PINCH, CLOSE }

class HandTracker(
    context: Context,
    private val onHandState: (HandState, HandLandmarker?) -> Unit
) {
    private var landmarker: HandLandmarker? = null
    @Volatile private var running = false

    init {
        try {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
            val opts = HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            landmarker = HandLandmarker.createFromOptions(context, opts)
        } catch (e: Exception) {
            Log.e("HandTracker", "init failed: ${e.message}")
        }
    }

    fun start() { running = true }
    fun stop() { running = false; landmarker?.close(); landmarker = null }

    fun processFrame(bitmap: Bitmap) {
        if (!running) return
        val lm = landmarker ?: return
        try {
            val mpImage = MPImage.bitmapBuilder(bitmap).build()
            val result = lm.detectForVideo(mpImage, System.currentTimeMillis())
            if (result.landmarks().isNotEmpty()) {
                onHandState(classify(result.landmarks()[0]), lm)
            } else {
                onHandState(HandState.IDLE, lm)
            }
        } catch (e: Exception) {
            Log.e("HandTracker", "process failed: ${e.message}")
        }
    }

    private fun classify(lm: List<NormalizedLandmark>): HandState {
        if (lm.size < 21) return HandState.IDLE
        val thumb = lm[4]; val index = lm[8]
        val d = sqrt(
            (thumb.x() - index.x()) * (thumb.x() - index.x()) +
            (thumb.y() - index.y()) * (thumb.y() - index.y()) +
            (thumb.z() - index.z()) * (thumb.z() - index.z())
        )
        return when {
            d < 0.05f -> HandState.PINCH
            d > 0.15f -> HandState.OPEN
            else -> HandState.IDLE
        }
    }
}
