package com.ml.virtualbackground.camera.ext

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.view.Surface
import com.google.android.gms.common.util.concurrent.HandlerExecutor

fun CameraDevice.getCaptureSession(
    targets: List<Surface>,
    handler: Handler,
    callback: (captureSession: CameraCaptureSession?) -> Unit
) {
    val stateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(captureSession: CameraCaptureSession) {
            callback(captureSession)
        }

        override fun onClosed(session: CameraCaptureSession) {
            callback(null)
            super.onClosed(session)
        }

        override fun onConfigureFailed(captureSession: CameraCaptureSession) {
            callback(null)
        }
    }

    val sessionConfiguration = SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        targets.map { OutputConfiguration(it) },
        HandlerExecutor(handler.looper),
        stateCallback
    )

    // Create a capture session using the predefined targets; this also involves defining the
    // session state callback to be notified of when the session is ready
    createCaptureSession(sessionConfiguration)
}
