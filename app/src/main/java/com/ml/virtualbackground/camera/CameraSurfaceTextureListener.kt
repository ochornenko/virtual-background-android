package com.ml.virtualbackground.camera

import com.ml.virtualbackground.camera.preview.CameraSurfaceTexture

interface CameraSurfaceTextureListener {
    fun onSurfaceReady(cameraSurfaceTexture: CameraSurfaceTexture)
}
