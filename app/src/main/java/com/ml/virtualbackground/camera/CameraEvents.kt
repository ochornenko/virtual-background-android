package com.ml.virtualbackground.camera

interface CameraEvents {
    fun onCameraOpened(cameraAttributes: CameraAttributes)
    fun onPreviewStarted()
}
