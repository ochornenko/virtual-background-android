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
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.ml.virtualbackground.camera.CameraSurfaceTextureListener
import com.ml.virtualbackground.camera.FpsListener
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraSurfaceView : GLSurfaceView, GLSurfaceView.Renderer {
    private var surfaceView = create()

    var listener: FpsListener? = null
    var cameraSurfaceTextureListener: CameraSurfaceTextureListener? = null

    private var cameraSurfaceTexture: CameraSurfaceTexture? = null
    private val textures = IntArray(3)
    private var frameCount = 0
    private var startTime = System.nanoTime()

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

        calculateFps()
    }

    fun release() {
        nativeRelease(surfaceView)

        GLES20.glDeleteTextures(3, textures, 0)
    }

    private fun genTextures(textureCallback: (inputTexture: Int, outputTexture: Int, backgroundTexture: Int) -> Unit) {
        GLES20.glGenTextures(3, textures, 0)
        textureCallback(textures[0], textures[1], textures[2])
    }

    private fun calculateFps() {
        frameCount++

        val currentTime = System.nanoTime()
        val elapsedTime = (currentTime - startTime) * 1e-9 // Convert to seconds

        if (elapsedTime >= 1.0) { // Update FPS every second
            val fps = frameCount / elapsedTime.toFloat()

            listener?.onFpsUpdate(fps)

            // Reset for next interval
            frameCount = 0
            startTime = System.nanoTime()
        }
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
