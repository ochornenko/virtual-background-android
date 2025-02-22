package com.ml.virtualbackground.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
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

    private var previewStarted = false

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun open(facing: CameraFacing) {
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
        previewStarted = false
    }

    @Synchronized
    override fun startPreview(surfaceTexture: SurfaceTexture) {
        val cameraDevice = cameraDevice
        if (cameraDevice != null) {
            val surface = Surface(surfaceTexture)
            cameraDevice.getCaptureSession(listOf(surface), cameraHandler) { session ->
                session?.let {
                    captureSession = it

                    val requestBuilder = cameraDevice
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder.addTarget(surface)

                    requestBuilder.set(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(FPS, FPS)
                    )

                    it.setRepeatingRequest(
                        requestBuilder.build(), null, cameraHandler
                    )
                    onPreviewStarted()
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
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
        previewStarted = false
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

    companion object {
        private val TAG: String = Camera2::class.java.simpleName
        private const val FPS = 30
    }
}
