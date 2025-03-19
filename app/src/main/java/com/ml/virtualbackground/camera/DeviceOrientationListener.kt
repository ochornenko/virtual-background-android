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
import android.view.OrientationEventListener
import android.view.Surface

class DeviceOrientationListener(context: Context) : OrientationEventListener(context) {

    var currentRotation: Int = 0

    override fun onOrientationChanged(orientation: Int) {
        if (orientation != ORIENTATION_UNKNOWN) {
            // Map the raw orientation to one of the four possible rotations (0, 90, 180, 270)
            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            currentRotation = rotation
        }
    }
}
