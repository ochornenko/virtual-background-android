package com.ml.virtualbackground.camera.preview

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import com.ml.virtualbackground.camera.Camera2
import com.ml.virtualbackground.camera.CameraApi
import com.ml.virtualbackground.camera.CameraAttributes
import com.ml.virtualbackground.camera.CameraEvents
import com.ml.virtualbackground.camera.CameraHandlerApi
import com.ml.virtualbackground.camera.CameraSizeCalculator
import com.ml.virtualbackground.camera.CameraSurfaceTextureListener
import com.ml.virtualbackground.camera.DeviceOrientationListener
import com.ml.virtualbackground.camera.FpsListener
import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize
import com.ml.virtualbackground.camera.utils.Utils.Companion.resizeBitmapToFit
import kotlinx.coroutines.CoroutineDispatcher
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
class CameraPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), CameraEvents {

    var lifecycleState: LifecycleState = LifecycleState.STOPPED
    var listener: FpsListener? = null

    private var orientationListener = DeviceOrientationListener(context.applicationContext)

    private var previewOrientation: Int = 0
    private var captureOrientation: Int = 0

    private var previewSize: CameraSize = CameraSize(0, 0)

    private var cameraFacing: CameraFacing = CameraFacing.FRONT
    private var surfaceTexture: CameraSurfaceTexture? = null
    private var attributes: CameraAttributes? = null

    private val cameraSurfaceView: CameraSurfaceView = CameraSurfaceView(context.applicationContext)

    private val cameraDispatcher: CoroutineDispatcher = newSingleThreadContext("camera")
    private var cameraOpenContinuation: Continuation<Unit>? = null
    private var previewStartContinuation: Continuation<Unit>? = null

    private val cameraApi: CameraApi = CameraHandlerApi(
        Camera2(this, context.applicationContext)
    )

    init {
        cameraSurfaceView.cameraSurfaceTextureListener = object : CameraSurfaceTextureListener {
            override fun onSurfaceReady(cameraSurfaceTexture: CameraSurfaceTexture) {
                surfaceTexture = cameraSurfaceTexture
                surfaceTexture?.init(context.applicationContext)

                if (lifecycleState == LifecycleState.STARTED || lifecycleState == LifecycleState.RESUMED) {
                    resume()
                }
            }
        }

        cameraSurfaceView.listener = object : FpsListener {
            override fun onFpsUpdate(fps: Float) {
                listener?.onFpsUpdate(fps)
            }
        }

        addView(cameraSurfaceView)
    }

    fun onStart() {
        start(cameraFacing)
    }

    fun onStop() {
        stop()
    }

    fun onResume() {
        if (surfaceTexture == null) {
            return
        }

        resume()
    }

    fun onPause() {
        pause()
    }

    fun onDestroy() {
        surfaceTexture?.release()
        cameraSurfaceView.release()
    }

    fun updateBackgroundImage(bitmap: Bitmap) {
        surfaceTexture?.updateBackgroundImage(resizeBitmapToFit(bitmap, VIDEO_WIDTH, VIDEO_HEIGHT))
    }

    private fun start(facing: CameraFacing) {
        GlobalScope.launch(cameraDispatcher) {
            runBlocking {
                lifecycleState = LifecycleState.STARTED
                cameraFacing = facing
                openCamera()
            }
        }
    }

    private fun resume() {
        GlobalScope.launch(cameraDispatcher) {
            lifecycleState = LifecycleState.RESUMED
            try {
                startPreview()
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
    }

    private fun pause() {
        lifecycleState = LifecycleState.PAUSED
        stopPreview()
    }

    private fun stop() {
        lifecycleState = LifecycleState.STOPPED
        closeCamera()
    }

    override fun onCameraOpened(cameraAttributes: CameraAttributes) {
        attributes = cameraAttributes
        cameraOpenContinuation?.resume(Unit)
        cameraOpenContinuation = null
    }

    override fun onPreviewStarted() {
        previewStartContinuation?.resume(Unit)
        previewStartContinuation = null
    }

    enum class LifecycleState {
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED;
    }

    private suspend fun openCamera(): Unit = suspendCoroutine {
        cameraOpenContinuation = it
        cameraApi.open(cameraFacing)
    }

    private suspend fun startPreview(): Unit = suspendCoroutine {
        previewStartContinuation = it

        val displayOrientation = orientationListener.currentRotation
        val surfaceTexture = surfaceTexture
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

            cameraApi.startPreview(surfaceTexture)
        } else {
            it.resumeWithException(IllegalStateException())
            previewStartContinuation = null
        }
    }

    private fun stopPreview() {
        cameraApi.stopPreview()
    }

    private fun closeCamera() {
        cameraApi.close()
    }

    companion object {
        private val TAG: String = CameraPreview::class.java.simpleName

        const val VIDEO_WIDTH = 720
        const val VIDEO_HEIGHT = 1280
    }
}
