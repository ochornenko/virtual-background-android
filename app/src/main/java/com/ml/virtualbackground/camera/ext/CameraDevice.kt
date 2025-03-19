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
