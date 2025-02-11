package com.ml.virtualbackground.camera

import android.os.Handler
import android.os.HandlerThread

class CameraHandler private constructor(thread: HandlerThread) : Handler(thread.looper) {
    companion object {
        fun get(): CameraHandler {
            val cameraThread = HandlerThread("CameraThread").apply { start() }

            return CameraHandler(cameraThread)
        }
    }
}
