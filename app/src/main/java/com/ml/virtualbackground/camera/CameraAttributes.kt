package com.ml.virtualbackground.camera

import com.ml.virtualbackground.camera.type.CameraFacing
import com.ml.virtualbackground.camera.type.CameraSize

interface CameraAttributes {
    val facing: CameraFacing
    val sensorOrientation: Int
    val previewSizes: Array<CameraSize>
    val photoSizes: Array<CameraSize>
}
