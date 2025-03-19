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
