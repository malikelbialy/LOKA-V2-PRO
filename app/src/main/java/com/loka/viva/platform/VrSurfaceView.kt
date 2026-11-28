package com.loka.viva.platform

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * GLSurfaceView that hosts the VrRenderer.
 */
class VrSurfaceView(context: Context, attrs: AttributeSet?) : GLSurfaceView(context, attrs) {

    private var vrRenderer: VrRenderer? = null

    fun setVrRenderer(renderer: VrRenderer) {
        vrRenderer = renderer
        setRenderer(renderer as GLSurfaceView.Renderer)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override fun onDetachedFromWindow() {
        vrRenderer?.stop()
        super.onDetachedFromWindow()
    }
}