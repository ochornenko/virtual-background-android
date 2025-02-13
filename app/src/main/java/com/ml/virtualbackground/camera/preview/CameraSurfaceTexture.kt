package com.ml.virtualbackground.camera.preview

import android.content.Context
import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.Matrix
import com.ml.virtualbackground.camera.type.CameraSize

class CameraSurfaceTexture(
    private val inputTexture: Int,
    val outputTexture: Int,
    private val backgroundTexture: Int,
) :
    SurfaceTexture(inputTexture) {
    private var surfaceTexture = create()
    var size: CameraSize = CameraSize(0, 0)
        set(size) {
            field = size
            previewInvalidated = true
        }

    private var previewInvalidated = false
    private val transformMatrix: FloatArray = FloatArray(16)
    private val extraTransformMatrix: FloatArray = FloatArray(16)

    fun init(context: Context) {
        nativeInit(
            context.assets,
            surfaceTexture,
            inputTexture,
            outputTexture,
            backgroundTexture
        )
        Matrix.setIdentityM(extraTransformMatrix, 0)
    }

    override fun updateTexImage() {
        if (previewInvalidated) {
            nativeSetSize(surfaceTexture, size.width, size.height)
            previewInvalidated = false
        }

        super.updateTexImage()
        getTransformMatrix(transformMatrix)
        nativeUpdateTexImage(surfaceTexture, transformMatrix, extraTransformMatrix)
    }

    override fun release() {
        nativeRelease(surfaceTexture)
    }

    fun setRotation(degrees: Int) {
        Matrix.setIdentityM(extraTransformMatrix, 0)
        Matrix.rotateM(extraTransformMatrix, 0, degrees.toFloat(), 0f, 0f, 1f)
    }

    private external fun create(): Long

    private external fun nativeInit(
        assetManager: AssetManager,
        surfaceTexture: Long,
        inputTexture: Int,
        outputTexture: Int,
        backgroundTexture: Int
    )

    private external fun nativeSetSize(surfaceTexture: Long, width: Int, height: Int)

    private external fun nativeUpdateTexImage(
        surfaceTexture: Long,
        transformMatrix: FloatArray,
        extraTransformMatrix: FloatArray
    )

    private external fun nativeRelease(surfaceTexture: Long)
}
