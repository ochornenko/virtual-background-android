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
import com.ml.virtualbackground.camera.type.CameraFacing

class CameraController private constructor(
    private val delegate: CameraApi
) : CameraApi by delegate {

    override fun open(facing: CameraFacing) {
        cameraHandler.run { delegate.open(facing) }
    }

    override fun close() {
        cameraHandler.run { delegate.close() }
    }

    override fun startPreview(surfaceTexture: SurfaceTexture) {
        cameraHandler.run { delegate.startPreview(surfaceTexture) }
    }

    override fun stopPreview() {
        cameraHandler.run { delegate.stopPreview() }
    }

    companion object {
        fun create(context: Context, delegate: CameraEvents): CameraController {
            return CameraController(Camera2(context, delegate))
        }
    }
}
