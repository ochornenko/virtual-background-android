package com.ml.virtualbackground.camera.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri

class Utils {
    companion object {
        fun resizeBitmapToFill(inputBitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val widthScale = targetWidth.toFloat() / inputBitmap.width
            val heightScale = targetHeight.toFloat() / inputBitmap.height
            val scaleFactor = maxOf(widthScale, heightScale) // Ensure the image fills the space

            val newWidth = (inputBitmap.width * scaleFactor).toInt()
            val newHeight = (inputBitmap.height * scaleFactor).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, newWidth, newHeight, true)

            val xOffset = (newWidth - targetWidth) / 2
            val yOffset = (newHeight - targetHeight) / 2

            return Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, targetWidth, targetHeight)
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

        private fun rotateBitmap(bitmap: Bitmap, degrees: Float) = Matrix().run {
            postRotate(degrees)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, this, true)
        }
    }
}
