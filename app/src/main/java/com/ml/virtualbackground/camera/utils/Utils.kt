package com.ml.virtualbackground.camera.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri

class Utils {
    companion object {
        fun resizeBitmapToFit(inputBitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val scaleFactor = targetWidth.toFloat() / inputBitmap.width
            val newHeight = (inputBitmap.height * scaleFactor).toInt()

            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).apply {
                    drawColor(Color.BLACK) // Fill with black background
                    drawBitmap(
                        Bitmap.createScaledBitmap(inputBitmap, targetWidth, newHeight, true),
                        0f,
                        ((targetHeight - newHeight) / 2f),
                        null
                    )
                }
            }
        }

        fun loadBitmap(context: Context, uri: Uri): Bitmap? {
            return context.contentResolver.openInputStream(uri)?.use { inputStream ->
                rotateIfNeeded(BitmapFactory.decodeStream(inputStream))
            }
        }

        private fun rotateIfNeeded(bitmap: Bitmap): Bitmap {
            return if (bitmap.width > bitmap.height) {
                rotateBitmap(bitmap, 90f) // Assuming landscape needs rotation
            } else {
                bitmap
            }
        }

        private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
