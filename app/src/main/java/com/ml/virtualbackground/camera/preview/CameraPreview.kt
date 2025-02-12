package com.ml.virtualbackground.camera.preview

import android.content.Context
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
import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize
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
    var surfaceState: SurfaceState = SurfaceState.SURFACE_WAITING

    private var cameraState: CameraState = CameraState.CAMERA_CLOSED

    private var orientationListener = DeviceOrientationListener(context)

    private var previewOrientation: Int = 0
    private var captureOrientation: Int = 0

    private var previewSize: CameraSize = CameraSize(0, 0)

    private var cameraFacing: CameraFacing = CameraFacing.FRONT
    private var surfaceTexture: CameraSurfaceTexture? = null
    private var attributes: CameraAttributes? = null

    private val cameraSurfaceView: CameraSurfaceView = CameraSurfaceView(context)

    private val cameraDispatcher: CoroutineDispatcher = newSingleThreadContext("camera")
    private var cameraOpenContinuation: Continuation<Unit>? = null
    private var previewStartContinuation: Continuation<Unit>? = null

    private val cameraApi: CameraApi = CameraHandlerApi(
        Camera2(this, context)
    )

    init {
        cameraSurfaceView.cameraSurfaceTextureListener = object : CameraSurfaceTextureListener {
            override fun onSurfaceReady(cameraSurfaceTexture: CameraSurfaceTexture) {
                surfaceTexture = cameraSurfaceTexture
                surfaceState = SurfaceState.SURFACE_AVAILABLE
                if (lifecycleState == LifecycleState.STARTED || lifecycleState == LifecycleState.RESUMED) {
                    resume()
                }
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

    fun resume() {
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
        cameraState = CameraState.CAMERA_OPENED
        attributes = cameraAttributes
        cameraOpenContinuation?.resume(Unit)
        cameraOpenContinuation = null
    }

    override fun onCameraClosed() {
        cameraState = CameraState.CAMERA_CLOSED
    }

    override fun onPreviewStarted() {
        cameraState = CameraState.PREVIEW_STARTED
        previewStartContinuation?.resume(Unit)
        previewStartContinuation = null
    }

    override fun onPreviewStopped() {
        cameraState = CameraState.PREVIEW_STOPPED
    }

    enum class LifecycleState {
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED;
    }

    enum class SurfaceState {
        SURFACE_AVAILABLE,
        SURFACE_WAITING;
    }

    enum class CameraState {
        CAMERA_OPENING,
        CAMERA_OPENED,
        PREVIEW_STARTING,
        PREVIEW_STARTED,
        PREVIEW_STOPPING,
        PREVIEW_STOPPED,
        CAMERA_CLOSING,
        CAMERA_CLOSED;
    }

    private suspend fun openCamera(): Unit = suspendCoroutine {
        cameraOpenContinuation = it
        cameraState = CameraState.CAMERA_OPENING
        cameraApi.open(cameraFacing)
    }

    private suspend fun startPreview(): Unit = suspendCoroutine {
        previewStartContinuation = it

        val displayOrientation = orientationListener.currentRotation
        val surfaceTexture = surfaceTexture
        val attributes = attributes
        if (surfaceTexture != null && attributes != null) {
            cameraState = CameraState.PREVIEW_STARTING

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
        cameraState = CameraState.PREVIEW_STOPPING
        cameraApi.stopPreview()
    }

    private fun closeCamera() {
        cameraState = CameraState.CAMERA_CLOSING
        cameraApi.close()
    }

    companion object {
        private val TAG: String = Camera2::class.java.simpleName

        const val VIDEO_WIDTH = 720
        const val VIDEO_HEIGHT = 1280
    }
}
