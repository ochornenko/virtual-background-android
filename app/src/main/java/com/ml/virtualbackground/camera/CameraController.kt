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
