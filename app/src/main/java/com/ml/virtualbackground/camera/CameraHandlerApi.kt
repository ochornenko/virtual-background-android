package com.ml.virtualbackground.camera

import android.graphics.SurfaceTexture
import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize

class CameraHandlerApi(private val delegate: CameraApi) : CameraApi by delegate {
    @Synchronized
    override fun open(facing: CameraFacing) {
        cameraHandler.run { delegate.open(facing) }
    }

    @Synchronized
    override fun close() {
        cameraHandler.run { delegate.close() }
    }

    override fun setPreviewSize(size: CameraSize) {
        cameraHandler.run { delegate.setPreviewSize(size) }
    }

    @Synchronized
    override fun startPreview(surfaceTexture: SurfaceTexture) {
        cameraHandler.run { delegate.startPreview(surfaceTexture) }
    }

    @Synchronized
    override fun stopPreview() {
        cameraHandler.run { delegate.stopPreview() }
    }

    @Synchronized
    override fun setPhotoSize(size: CameraSize) {
        cameraHandler.run { delegate.setPhotoSize(size) }
    }

    @Synchronized
    override fun capturePhoto(callback: (jpeg: ByteArray) -> Unit) {
        cameraHandler.run { delegate.capturePhoto(callback) }
    }
}
