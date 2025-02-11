package com.ml.virtualbackground.camera

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface

class DeviceOrientationListener(context: Context) : OrientationEventListener(context) {

    var currentRotation: Int = 0

    override fun onOrientationChanged(orientation: Int) {
        if (orientation != ORIENTATION_UNKNOWN) {
            // Map the raw orientation to one of the four possible rotations (0, 90, 180, 270)
            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            currentRotation = rotation
        }
    }
}
