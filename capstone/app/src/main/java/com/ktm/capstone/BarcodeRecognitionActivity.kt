package com.ktm.capstone

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.math.abs

class BarcodeRecognitionActivity : AppCompatActivity() {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var viewFinder: PreviewView
    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var gestureDetector: GestureDetector
    private var yStart = 0f
    private var isImageDisplayed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_recognition)

        viewFinder = findViewById(R.id.viewFinder)
        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isImageDisplayed) {
                    captureAndAnalyze()
                } else {
                    resetToInitialView()
                }
                return true
            }
        })

        initializeCamera()
        barcodeScanner = BarcodeScanning.getClient()
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder().build()
                imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (e: InterruptedException) {
                Log.e("BarcodeRecognition", "Camera initialization failed", e)
            } catch (e: ExecutionException) {
                Log.e("BarcodeRecognition", "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        val photoFile = File.createTempFile("barcode", ".jpg", externalCacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    imageView.setImageURI(savedUri)
                    imageView.visibility = View.VISIBLE
                    isImageDisplayed = true
                    analyzeImage(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("BarcodeRecognition", "Photo capture failed", exception)
                }
            })
    }

    private fun analyzeImage(photoFile: File) {
        try {
            val image = InputImage.fromFilePath(this, Uri.fromFile(photoFile))
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        resultTextView.text = rawValue
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeRecognition", "Barcode processing failed", e)
                }
        } catch (e: IOException) {
            Log.e("BarcodeRecognition", "Image analysis failed", e)
        }
    }

    private fun resetToInitialView() {
        imageView.visibility = View.GONE
        resultTextView.text = ""
        isImageDisplayed = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.y
        } else if (action == MotionEvent.ACTION_UP) {
            val yEnd = event.y
            if (abs((yEnd - yStart).toDouble()) > 100) {
                finish()
            }
        }
        return super.onTouchEvent(event)
    }
}
