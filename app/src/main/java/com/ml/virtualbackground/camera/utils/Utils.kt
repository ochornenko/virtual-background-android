/*
 * Copyright 2025 Oleg Chornenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ml.virtualbackground.camera.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale

class Utils {
    companion object {
        fun resizeBitmapToFill(inputBitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            val widthScale = targetWidth.toFloat() / inputBitmap.width
            val heightScale = targetHeight.toFloat() / inputBitmap.height
            val scaleFactor = maxOf(widthScale, heightScale) // Ensure the image fills the space

            val newWidth = (inputBitmap.width * scaleFactor).toInt()
            val newHeight = (inputBitmap.height * scaleFactor).toInt()

            val scaledBitmap = inputBitmap.scale(newWidth, newHeight)

            val xOffset = (newWidth - targetWidth) / 2
            val yOffset = (newHeight - targetHeight) / 2

            return createBitmap(scaledBitmap, xOffset, yOffset, targetWidth, targetHeight)
        }

        fun loadBitmap(context: Context, uri: Uri): Bitmap? {
            return context.contentResolver.openInputStream(uri)?.use { inputStream ->
                rotateIfNeeded(BitmapFactory.decodeStream(inputStream))
            }
        }

        private fun rotateIfNeeded(bitmap: Bitmap): Bitmap {
            return if (bitmap.width > bitmap.height) {
                rotateBitmap(bitmap) // Assuming landscape needs rotation
            } else {
                bitmap
            }
        }

        private fun rotateBitmap(bitmap: Bitmap, degrees: Float = 90f) = Matrix().run {
            postRotate(degrees)
            createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, this, true)
        }
    }
}
