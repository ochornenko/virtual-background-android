package com.ml.virtualbackground.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.util.Log
import android.view.Surface
import com.ml.virtualbackground.camera.ext.getCameraId
import com.ml.virtualbackground.camera.ext.getCaptureSession
import com.ml.virtualbackground.camera.ext.getPhotoSizes
import com.ml.virtualbackground.camera.ext.getPreviewSizes
import com.ml.virtualbackground.camera.ext.getSensorOrientation
import com.ml.virtualbackground.camera.ext.whenDeviceAvailable
import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize

class Camera2(eventsDelegate: CameraEvents, context: Context) :
    CameraApi, CameraEvents by eventsDelegate {

    override val cameraHandler: CameraHandler = CameraHandler.get()

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var cameraAttributes: CameraAttributes? = null

    private var captureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null

    private var imageReader: ImageReader? = null
    private var photoCallback: ((jpeg: ByteArray) -> Unit)? = null

    private var previewStarted = false
    private var cameraFacing: CameraFacing = CameraFacing.BACK
    private var waitingFrames: Int = 0

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun open(facing: CameraFacing) {
        cameraFacing = facing
        val cameraId = cameraManager.getCameraId(facing) ?: throw RuntimeException()
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        cameraManager.whenDeviceAvailable(cameraId, cameraHandler) {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cameraDevice: CameraDevice) {
                    val cameraAttributes = Attributes(cameraCharacteristics, facing)
                    this@Camera2.cameraDevice = cameraDevice
                    this@Camera2.cameraAttributes = cameraAttributes
                    onCameraOpened(cameraAttributes)
                }

                override fun onDisconnected(cameraDevice: CameraDevice) {
                    cameraDevice.close()
                    this@Camera2.cameraDevice = null
                    this@Camera2.captureSession = null
                    onCameraClosed()
                }

                override fun onError(cameraDevice: CameraDevice, error: Int) {
                    cameraDevice.close()
                    this@Camera2.cameraDevice = null
                    this@Camera2.captureSession = null
                }
            }, cameraHandler)
        }
    }

    @Synchronized
    override fun close() {
        cameraDevice?.close()
        cameraDevice = null
        captureSession?.close()
        captureSession = null
        cameraAttributes = null
        imageReader?.close()
        imageReader = null
        previewStarted = false
        onCameraClosed()
    }

    @Synchronized
    override fun setPreviewSize(size: CameraSize) {
    }

    @Synchronized
    override fun startPreview(surfaceTexture: SurfaceTexture) {
        val cameraDevice = cameraDevice
        val imageReader = imageReader
        if (cameraDevice != null && imageReader != null) {
            val surface = Surface(surfaceTexture)
            cameraDevice.getCaptureSession(
                listOf(surface, imageReader.surface),
                cameraHandler
            ) { session ->

                session?.let {
                    captureSession = it

                    val requestBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(surface)
                    requestBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    requestBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CaptureRequest.CONTROL_MODE_AUTO
                    )

                    it.setRepeatingRequest(
                        requestBuilder.build(),
                        captureCallback,
                        cameraHandler
                    )
                    captureRequestBuilder = requestBuilder
                }
            }
        }
    }

    @Synchronized
    override fun stopPreview() {
        val captureSession = captureSession
        this.captureSession = null
        if (captureSession != null) {
            try {
                captureSession.stopRepeating()
                captureSession.abortCaptures()
                captureSession.close()
            } catch (_: Exception) {
            } finally {
                onPreviewStopped()
            }
        }
        previewStarted = false
    }

    @Synchronized
    override fun setPhotoSize(size: CameraSize) {
        this.imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
    }

    @Synchronized
    override fun capturePhoto(callback: (jpeg: ByteArray) -> Unit) {
        this.photoCallback = callback

        if (cameraFacing == CameraFacing.BACK) {
            lockFocus()
        } else {
            captureStillPicture()
        }
    }

    private fun lockFocus() {
        val requestBuilder = captureRequestBuilder
        val captureSession = captureSession
        if (requestBuilder != null && captureSession != null) {
            try {
                requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START
                )
                captureState = STATE_WAITING_LOCK
                waitingFrames = 0
                captureSession.capture(
                    requestBuilder.build(),
                    captureCallback,
                    cameraHandler
                )
                requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
    }

    private fun runPreCaptureSequence() {
        val requestBuilder = captureRequestBuilder
        val captureSession = captureSession
        if (requestBuilder != null && captureSession != null) {
            requestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            captureState = STATE_WAITING_PRE_CAPTURE
            captureSession.capture(requestBuilder.build(), captureCallback, cameraHandler)
            requestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, null)
            captureSession.setRepeatingRequest(
                requestBuilder.build(),
                captureCallback,
                cameraHandler
            )
        }
    }

    private fun captureStillPicture() {
        val captureSession = captureSession
        val cameraDevice = cameraDevice
        val imageReader = imageReader
        if (captureSession != null && cameraDevice != null && imageReader != null) {
            val captureBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface)
            captureBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            cameraHandler.post {
                captureSession.capture(
                    captureBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            unlockFocus()
                        }
                    },
                    cameraHandler
                )
            }
        }
    }

    private fun unlockFocus() {
        val requestBuilder = captureRequestBuilder
        val captureSession = captureSession
        if (requestBuilder != null && captureSession != null) {
            requestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            captureSession.capture(requestBuilder.build(), captureCallback, cameraHandler)
            captureState = STATE_PREVIEW
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            captureSession.setRepeatingRequest(
                requestBuilder.build(),
                captureCallback,
                cameraHandler
            )
        }
    }

    private var captureState: Int = STATE_PREVIEW

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (captureState) {
                STATE_PREVIEW -> {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        photoCallback?.invoke(bytes)
                        photoCallback = null
                        image.close()
                    }
                }

                STATE_WAITING_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        runPreCaptureSequence()
                    } else if (null == afState || CaptureResult.CONTROL_AF_STATE_INACTIVE == afState) {
                        captureStillPicture()
                    } else if (waitingFrames >= 5) {
                        waitingFrames = 0
                        captureStillPicture()
                    } else {
                        waitingFrames++
                    }
                }

                STATE_WAITING_PRE_CAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                    ) {
                        captureState = STATE_WAITING_NON_PRE_CAPTURE
                    }
                }

                STATE_WAITING_NON_PRE_CAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        captureState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            if (!previewStarted) {
                onPreviewStarted()
                previewStarted = true
            }

            process(result)
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }
    }

    companion object {
        private val TAG: String = Camera2::class.java.simpleName

        private const val STATE_PREVIEW = 0
        private const val STATE_WAITING_LOCK = 1
        private const val STATE_WAITING_PRE_CAPTURE = 2
        private const val STATE_WAITING_NON_PRE_CAPTURE = 3
        private const val STATE_PICTURE_TAKEN = 4
    }

    private class Attributes(
        cameraCharacteristics: CameraCharacteristics,
        cameraFacing: CameraFacing
    ) : CameraAttributes {

        override val facing: CameraFacing = cameraFacing

        override val sensorOrientation: Int = cameraCharacteristics.getSensorOrientation()

        override val previewSizes: Array<CameraSize> = cameraCharacteristics.getPreviewSizes()

        override val photoSizes: Array<CameraSize> = cameraCharacteristics.getPhotoSizes()
    }
}
