/*
 * Copyright 2025 Oleg Chornenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ml.virtualbackground.camera.preview

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.ml.virtualbackground.camera.type.CameraSize

class CameraSurfaceTexture(
    private val inputTexture: Int,
    val outputTexture: Int,
    private val backgroundTexture: Int
) : SurfaceTexture(inputTexture) {

    private var surfaceTexture = create()
    var size: CameraSize = CameraSize(0, 0)
        set(size) {
            field = size
            previewInvalidated = true
        }

    private var previewInvalidated = false
    private val transformMatrix: FloatArray = FloatArray(16)
    private val extraTransformMatrix: FloatArray = FloatArray(16)
    private var backgroundBitmap: Bitmap? = null

    fun init(context: Context) {
        nativeInit(
            context.assets,
            surfaceTexture,
            inputTexture,
            outputTexture
        )
        Matrix.setIdentityM(extraTransformMatrix, 0)
    }

    override fun updateTexImage() {
        if (previewInvalidated) {
            val texture = backgroundBitmap?.let {
                updateTexture(it, backgroundTexture)
                backgroundTexture
            } ?: 0
            nativeSetParams(surfaceTexture, size.width, size.height, texture)
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

    fun updateBackgroundImage(bitmap: Bitmap) {
        backgroundBitmap = bitmap
        previewInvalidated = true
    }

    private fun updateTexture(bitmap: Bitmap, texture: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private external fun create(): Long

    private external fun nativeInit(
        assetManager: AssetManager,
        surfaceTexture: Long,
        inputTexture: Int,
        outputTexture: Int
    )

    private external fun nativeSetParams(
        surfaceTexture: Long,
        width: Int,
        height: Int,
        backgroundTexture: Int
    )

    private external fun nativeUpdateTexImage(
        surfaceTexture: Long,
        transformMatrix: FloatArray,
        extraTransformMatrix: FloatArray
    )

    private external fun nativeRelease(surfaceTexture: Long)
}
