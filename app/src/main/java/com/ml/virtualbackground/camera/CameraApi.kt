package com.ml.virtualbackground.camera

interface CameraApi : CameraActions, CameraEvents {
    val cameraHandler: CameraHandler
}
