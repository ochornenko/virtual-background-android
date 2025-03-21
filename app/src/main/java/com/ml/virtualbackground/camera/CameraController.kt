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

package com.ml.virtualbackground.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import com.ml.virtualbackground.camera.preview.CameraSurfaceTexture
import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(DelicateCoroutinesApi::class)
class CameraController private constructor(
    context: Context,
    private val delegate: CameraApi
) : CameraApi by delegate {

    enum class LifecycleState {
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED;
    }

    private var lifecycleState: LifecycleState = LifecycleState.STOPPED

    private val cameraDispatcher = newSingleThreadContext("camera")

    private var cameraFacing: CameraFacing = CameraFacing.FRONT

    private var orientationListener = DeviceOrientationListener(context)

    private var previewOrientation: Int = 0
    private var captureOrientation: Int = 0

    private var previewSize: CameraSize = CameraSize(0, 0)

    private var attributes: CameraAttributes? = null
    private var cameraOpenContinuation: Continuation<Unit>? = null
    private var previewStartContinuation: Continuation<Unit>? = null

    override fun onCameraOpened(cameraAttributes: CameraAttributes) {
        attributes = cameraAttributes
        cameraOpenContinuation?.resume(Unit)
        cameraOpenContinuation = null
    }

    override fun onPreviewStarted() {
        previewStartContinuation?.resume(Unit)
        previewStartContinuation = null
    }

    fun resumePreview(surfaceTexture: CameraSurfaceTexture?) {
        GlobalScope.launch(cameraDispatcher) {
            try {
                resume(surfaceTexture)
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
    }

    private suspend fun resume(surfaceTexture: CameraSurfaceTexture?): Unit =
        suspendCoroutine {
            previewStartContinuation = it

            val displayOrientation = orientationListener.currentRotation
            val attributes = attributes
            if (surfaceTexture != null && attributes != null) {
                previewOrientation = when (cameraFacing) {
                    CameraFacing.BACK -> (attributes.sensorOrientation - displayOrientation + 360) % 360
                    CameraFacing.FRONT -> {
                        val result = (attributes.sensorOrientation + displayOrientation) % 360
                        (360 - result) % 360
                    }
                }

                captureOrientation = when (cameraFacing) {
                    CameraFacing.BACK -> (attributes.sensorOrientation - displayOrientation + 360) % 360
                    CameraFacing.FRONT -> (attributes.sensorOrientation + displayOrientation + 360) % 360
                }

                surfaceTexture.setRotation(displayOrientation)

                previewSize = CameraSizeCalculator(attributes.previewSizes)
                    .findClosestSizeContainingTarget(
                        when (previewOrientation % 180 == 0) {
                            true -> CameraSize(VIDEO_WIDTH, VIDEO_HEIGHT)
                            false -> CameraSize(VIDEO_HEIGHT, VIDEO_WIDTH)
                        }
                    )

                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                surfaceTexture.size = when (previewOrientation % 180) {
                    0 -> previewSize
                    else -> CameraSize(previewSize.height, previewSize.width)
                }

                startPreview(surfaceTexture)
            } else {
                it.resumeWithException(IllegalStateException())
                previewStartContinuation = null
            }
        }

    fun openCamera() {
        GlobalScope.launch(cameraDispatcher) {
            runBlocking {
                open()
            }
        }
    }

    private suspend fun open(): Unit = suspendCoroutine {
        cameraOpenContinuation = it
        open(cameraFacing)
    }

    override fun open(facing: CameraFacing) {
        lifecycleState = LifecycleState.STARTED

        cameraHandler.run { delegate.open(facing) }
    }

    override fun close() {
        lifecycleState = LifecycleState.STOPPED

        cameraHandler.run { delegate.close() }
    }

    override fun startPreview(surfaceTexture: SurfaceTexture) {
        if (lifecycleState == LifecycleState.STARTED) {
            lifecycleState = LifecycleState.RESUMED

            cameraHandler.run { delegate.startPreview(surfaceTexture) }
        }
    }

    override fun stopPreview() {
        lifecycleState = LifecycleState.PAUSED

        cameraHandler.run { delegate.stopPreview() }
    }

    companion object {
        const val VIDEO_WIDTH = 720
        const val VIDEO_HEIGHT = 1280

        private val TAG: String = CameraController::class.java.simpleName

        fun create(context: Context, eventsDelegate: CameraEvents): CameraController {
            return CameraController(context, Camera2(context, eventsDelegate))
        }
    }
}
