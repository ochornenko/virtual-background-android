package com.ml.virtualbackground.camera

import android.graphics.SurfaceTexture
import com.ml.virtualbackground.camera.type.CameraFacing

interface CameraActions {
    fun open(facing: CameraFacing)
    fun close()
    fun startPreview(surfaceTexture: SurfaceTexture)
    fun stopPreview()
}
