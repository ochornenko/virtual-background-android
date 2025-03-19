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

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import com.ml.virtualbackground.camera.type.CameraFacing

fun CameraManager.getCameraId(facing: CameraFacing): String? {
    val targetFacingCharacteristic = when (facing) {
        CameraFacing.BACK -> CameraCharacteristics.LENS_FACING_BACK
        CameraFacing.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
    }

    return cameraIdList.find { cameraId ->
        val characteristics = getCameraCharacteristics(cameraId)
        val facingCharacteristic = characteristics.get(CameraCharacteristics.LENS_FACING)
        facingCharacteristic == targetFacingCharacteristic
    }
}

fun CameraManager.whenDeviceAvailable(
    targetCameraId: String,
    handler: Handler,
    callback: () -> Unit
) {
    registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            if (cameraId == targetCameraId) {
                unregisterAvailabilityCallback(this)
                callback()
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
        }
    }, handler)
}
