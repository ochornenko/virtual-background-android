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

package com.ml.virtualbackground

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ml.virtualbackground.camera.CameraController
import com.ml.virtualbackground.camera.CameraController.Companion.VIDEO_HEIGHT
import com.ml.virtualbackground.camera.CameraController.Companion.VIDEO_WIDTH
import com.ml.virtualbackground.camera.CameraSurfaceTextureListener
import com.ml.virtualbackground.camera.FpsListener
import com.ml.virtualbackground.camera.preview.CameraSurfaceTexture
import com.ml.virtualbackground.camera.utils.Utils.Companion.loadBitmap
import com.ml.virtualbackground.camera.utils.Utils.Companion.resizeBitmapToFill
import com.ml.virtualbackground.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController

    private var cameraSurfaceTexture: CameraSurfaceTexture? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setup()
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) {
            cameraController.openCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            cameraController.resumePreview(cameraSurfaceTexture)
        }
    }

    override fun onPause() {
        if (hasCameraPermission()) {
            cameraController.stopPreview()
        }
        super.onPause()
    }

    override fun onStop() {
        if (hasCameraPermission()) {
            cameraController.close()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (hasCameraPermission()) {
            cameraSurfaceTexture?.release()
            binding.cameraPreview.release()
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraController.openCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
        )
    }

    private fun setup() {
        cameraController = CameraController.create(applicationContext)

        binding.imageButton.setOnClickListener {
            openMediaPicker()
        }

        binding.cameraPreview.surfaceTextureListener = object : CameraSurfaceTextureListener {
            override fun onSurfaceReady(surfaceTexture: CameraSurfaceTexture) {
                cameraSurfaceTexture = surfaceTexture
                cameraController.resumePreview(surfaceTexture)
            }
        }

        binding.cameraPreview.listener = object : FpsListener {
            override fun onFpsUpdate(fps: Float) {
                runOnUiThread {
                    binding.fps.text = getString(R.string.fps).format(fps)
                }
            }
        }
    }

    private fun openMediaPicker() {
        mediaPickerLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    private var mediaPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
            it?.let { uri ->
                loadBitmap(applicationContext, uri)?.let { bitmap ->
                    cameraSurfaceTexture?.updateBackgroundImage(
                        resizeBitmapToFill(bitmap, VIDEO_WIDTH, VIDEO_HEIGHT)
                    )
                }
            }
        }

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100

        init {
            System.loadLibrary("native-lib")
        }
    }
}
