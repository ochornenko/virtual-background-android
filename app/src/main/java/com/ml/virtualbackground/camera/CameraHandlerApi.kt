package com.ml.virtualbackground.camera

import android.graphics.SurfaceTexture
import com.ml.virtualbackground.camera.type.CameraFacing

class CameraHandlerApi(private val delegate: CameraApi) : CameraApi by delegate {
    @Synchronized
    override fun open(facing: CameraFacing) {
        cameraHandler.run { delegate.open(facing) }
    }

    @Synchronized
    override fun close() {
        cameraHandler.run { delegate.close() }
    }

    @Synchronized
    override fun startPreview(surfaceTexture: SurfaceTexture) {
        cameraHandler.run { delegate.startPreview(surfaceTexture) }
    }

    @Synchronized
    override fun stopPreview() {
        cameraHandler.run { delegate.stopPreview() }
    }
}
