package com.loka.viva.platform

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.loka.viva.logic.VWindowGeometry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Stereo VR renderer:
 *  - camera passthrough via an external OES texture fed by Camera2
 *  - digital windows drawn as textured quads in front of the user
 *  - head rotation applied to the view matrix of each eye
 */
class VrRenderer(
    private val context: Context,
    private val onSurfaceReady: () -> Unit,
    private val windowGeometryProvider: () -> VWindowGeometry,
    private val onCameraSurfaceReady: (Surface) -> Unit
) : GLSurfaceView.Renderer {

    private val vMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // camera OES texture
    private var cameraTexId = 0
    private var cameraTexture: SurfaceTexture? = null
    @Volatile private var cameraSurface: Surface? = null
    @Volatile private var newFrame = false

    private val windows = mutableListOf<VWindow>()
    private val headRotation = FloatArray(3)

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uMvp = 0
    private var uTex = 0

    @Volatile private var surfaceReady = false
    private var viewW = 0
    private var viewH = 0

    private val quadVerts = floatBuffer(floatArrayOf(
        -0.5f, -0.5f, 0f,
         0.5f, -0.5f, 0f,
         0.5f,  0.5f, 0f,
        -0.5f,  0.5f, 0f
    ))
    private val fullVerts = floatBuffer(floatArrayOf(
        -1f, -1f, 0f,
         1f, -1f, 0f,
         1f,  1f, 0f,
        -1f,  1f, 0f
    ))
    private val texCoords = floatBuffer(floatArrayOf(
        0f, 1f,
        1f, 1f,
        1f, 0f,
        0f, 0f
    ))

    private val vertexSrc = """
        uniform mat4 uMvp;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMvp * aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentSrc = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTex;
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = texture2D(uTex, vTexCoord);
        }
    """.trimIndent()

    fun updateHeadRotation(pitch: Float, yaw: Float, roll: Float) {
        headRotation[0] = pitch
        headRotation[1] = yaw
        headRotation[2] = roll
    }

    fun addWindow(w: VWindow) { windows.add(w) }
    fun removeWindow(w: VWindow) { windows.remove(w) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        program = buildProgram(vertexSrc, fragmentSrc)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uTex = GLES20.glGetUniformLocation(program, "uTex")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        cameraTexId = ids[0]
        GLES20.glBindTexture(GLES11.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
        GLES20.glTexParameteri(GLES11.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(cameraTexId)
        st.setOnFrameAvailableListener { newFrame = true }
        cameraTexture = st
        val surf = Surface(st)
        cameraSurface = surf
        onCameraSurfaceReady(surf)

        surfaceReady = true
        onSurfaceReady()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (newFrame) {
            try {
                cameraTexture?.updateTexImage()
                newFrame = false
            } catch (e: Exception) { /* ignore */ }
        }

        val halfW = viewW / 2
        drawEye(0, 0, halfW, viewH, -1f)
        drawEye(halfW, 0, halfW, viewH, 1f)
    }

    private fun drawEye(x: Int, y: Int, w: Int, h: Int, eyeSign: Float) {
        if (w <= 0 || h <= 0) return
        GLES20.glViewport(x, y, w, h)

        val aspect = w.toFloat() / h
        Matrix.perspectiveM(projMatrix, 0, 75f, aspect, 0.05f, 100f)

        val ipdHalf = 0.032f
        Matrix.setLookAtM(
            vMatrix, 0,
            eyeSign * ipdHalf, 0f, 0f,
            eyeSign * ipdHalf, 0f, -1f,
            0f, 1f, 0f
        )
        Matrix.rotateM(vMatrix, 0, Math.toDegrees(headRotation[0].toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(vMatrix, 0, Math.toDegrees(headRotation[1].toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(vMatrix, 0, Math.toDegrees(headRotation[2].toDouble()).toFloat(), 0f, 0f, 1f)

        GLES20.glUseProgram(program)

        // 1) passthrough (fullscreen, no depth)
        drawQuad(fullVerts, identityMvp(), cameraTexId)

        // 2) windows
        val geom = windowGeometryProvider()
        for (w0 in windows) {
            val model = FloatArray(16)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, geom.position.x, geom.position.y, geom.position.z)
            Matrix.scaleM(model, 0, geom.size.x, geom.size.y, 1f)

            val mvp = FloatArray(16)
            Matrix.multiplyMM(mvp, 0, vMatrix, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projMatrix, 0, mvp, 0)
            drawQuad(quadVerts, mvp, cameraTexId)
        }

        GLES20.glUseProgram(0)
    }

    private fun identityMvp(): FloatArray {
        Matrix.setIdentityM(mvpMatrix, 0)
        return mvpMatrix
    }

    private fun drawQuad(verts: FloatBuffer, mvp: FloatArray, texId: Int) {
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, verts)

        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(uTex, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    fun stop() {
        cameraTexture?.release()
        cameraTexture = null
        cameraSurface?.release()
        cameraSurface = null
        if (cameraTexId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(cameraTexId), 0)
            cameraTexId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        surfaceReady = false
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun floatBuffer(data: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        return bb.asFloatBuffer().apply { put(data); position(0) }
    }
}
