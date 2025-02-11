package com.ml.virtualbackground.camera

import android.graphics.SurfaceTexture
import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize

interface CameraActions {
    fun open(facing: CameraFacing)
    fun close()
    fun setPreviewSize(size: CameraSize)
    fun startPreview(surfaceTexture: SurfaceTexture)
    fun stopPreview()
    fun setPhotoSize(size: CameraSize)
    fun capturePhoto(callback: (jpeg: ByteArray) -> Unit)
}
