package com.ml.virtualbackground.camera.type

data class CameraSize(val width: Int, val height: Int) : Comparable<CameraSize> {
    fun area(): Int {
        return width * height
    }

    override fun compareTo(other: CameraSize): Int {
        val areaDiff = width * height - other.width * other.height
        return if (areaDiff > 0) {
            1
        } else if (areaDiff < 0) {
            -1
        } else {
            0
        }
    }
}
