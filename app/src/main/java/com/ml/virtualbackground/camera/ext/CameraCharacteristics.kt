package com.ml.virtualbackground.camera.ext

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.view.SurfaceHolder
import com.ml.virtualbackground.camera.type.CameraSize

fun CameraCharacteristics.getSensorOrientation(): Int {
    return get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
}

fun CameraCharacteristics.getPreviewSizes(): Array<CameraSize> {
    val streamConfigMap = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?: return emptyArray()

    val outputSizes = streamConfigMap.getOutputSizes(SurfaceHolder::class.java)
        ?: return emptyArray()

    return outputSizes
        .map { CameraSize(it.width, it.height) }
        .toTypedArray()
}

fun CameraCharacteristics.getPhotoSizes(): Array<CameraSize> {
    val streamConfigMap = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?: return emptyArray()

    val outputSizes = streamConfigMap.getOutputSizes(ImageFormat.JPEG)
        ?: return emptyArray()

    return outputSizes
        .map { CameraSize(it.width, it.height) }
        .toTypedArray()
}
