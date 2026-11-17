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
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
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

        /**
         * Decodes [uri] with its EXIF orientation applied, downsampled to the smallest
         * power-of-two size that still covers [targetWidth] x [targetHeight].
         */
        fun loadBitmap(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return try {
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    var sampleSize = 1
                    while (info.size.width / (sampleSize * 2) >= targetWidth &&
                        info.size.height / (sampleSize * 2) >= targetHeight
                    ) {
                        sampleSize *= 2
                    }
                    decoder.setTargetSampleSize(sampleSize)
                    // GLUtils.texImage2D can't upload hardware bitmaps
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                null
            }
        }

        private const val TAG = "Utils"
    }
}
