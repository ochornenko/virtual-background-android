package com.ml.virtualbackground.camera.preview

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.ml.virtualbackground.camera.CameraSurfaceTextureListener
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraSurfaceView : GLSurfaceView, GLSurfaceView.Renderer {
    private var surfaceView = create()

    var cameraSurfaceTextureListener: CameraSurfaceTextureListener? = null
    private var cameraSurfaceTexture: CameraSurfaceTexture? = null
    private val textures = IntArray(3)

    constructor(context: Context)
            : super(context)

    constructor(context: Context, attributeSet: AttributeSet)
            : super(context, attributeSet)

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        genTextures { inputTexture, outputTexture, backgroundTexture ->
            cameraSurfaceTexture = CameraSurfaceTexture(
                inputTexture,
                outputTexture,
                backgroundTexture
            ).apply {
                setOnFrameAvailableListener { requestRender() }
                cameraSurfaceTextureListener?.onSurfaceReady(this)
            }
        }

        nativeOnSurfaceCreated(surfaceView)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        nativeOnSurfaceChanged(surfaceView, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        val cameraSurfaceTexture = cameraSurfaceTexture
        if (cameraSurfaceTexture != null) {
            nativeOnDrawFrame(surfaceView)

            cameraSurfaceTexture.updateTexImage()
            nativeDrawTexture(
                surfaceView,
                cameraSurfaceTexture.outputTexture,
                cameraSurfaceTexture.size.width,
                cameraSurfaceTexture.size.height
            )
        }
    }

    fun release() {
        nativeRelease(surfaceView)

        GLES20.glDeleteTextures(3, textures, 0)
    }

    private fun genTextures(textureCallback: (inputTexture: Int, outputTexture: Int, backgroundTexture: Int) -> Unit) {
        GLES20.glGenTextures(3, textures, 0)
        textureCallback(textures[0], textures[1], textures[2])
    }

    private external fun create(): Long

    private external fun nativeOnSurfaceCreated(surfaceView: Long)

    private external fun nativeOnSurfaceChanged(surfaceView: Long, width: Int, height: Int)

    private external fun nativeOnDrawFrame(surfaceView: Long)

    private external fun nativeDrawTexture(
        surfaceView: Long,
        texture: Int,
        textureWidth: Int,
        textureHeight: Int
    )

    private external fun nativeRelease(surfaceView: Long)
}
