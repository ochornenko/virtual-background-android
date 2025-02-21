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
import com.ml.virtualbackground.camera.FpsListener
import com.ml.virtualbackground.camera.utils.Utils.Companion.loadBitmap
import com.ml.virtualbackground.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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
            binding.camera.onStart()
        } else {
            requestCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            binding.camera.onResume()
        }
    }

    override fun onPause() {
        if (hasCameraPermission()) {
            binding.camera.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        if (hasCameraPermission()) {
            binding.camera.onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (hasCameraPermission()) {
            binding.camera.onDestroy()
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
                binding.camera.onStart()
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
        binding.imageButton.setOnClickListener {
            openMediaPicker()
        }

        binding.camera.listener = object : FpsListener {
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
                    binding.camera.updateBackgroundImage(bitmap)
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
