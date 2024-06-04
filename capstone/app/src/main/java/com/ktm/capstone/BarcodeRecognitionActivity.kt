package com.ktm.capstone

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
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class BarcodeRecognitionActivity : AppCompatActivity() {
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var cameraClickSound: MediaPlayer? = null
    private var imageView: ImageView? = null
    private var barcodeTextView: TextView? = null
    private var gestureDetector: GestureDetector? = null
    private var isImageDisplayed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_recognition)

        imageView = findViewById(R.id.imageView)
        barcodeTextView = findViewById(R.id.barcodeTextView)
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click)
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
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder().build()
                val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
                imageCapture = ImageCapture.Builder().build()
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector!!,
                    preview,
                    imageCapture
                )
            } catch (e: ExecutionException) {
                Log.e("CameraXApp", "Use case binding failed", e)
            } catch (e: InterruptedException) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        imageView?.visibility = View.GONE
        barcodeTextView?.visibility = View.GONE
        val fileName = "pic_" + System.currentTimeMillis() + ".jpg"
        val photoFile = File(getExternalFilesDir(null), fileName)
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture?.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    cameraClickSound?.start()
                    val savedUri = Uri.fromFile(photoFile)
                    imageView?.setImageURI(savedUri)
                    imageView?.visibility = View.VISIBLE
                    isImageDisplayed = true
                    Log.d("CameraXApp", "Photo saved successfully: $savedUri")
                    analyzeImageForBarcode(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                }
            })
    }

    private fun analyzeImageForBarcode(photoFile: File) {
        val image = InputImage.fromFilePath(this, Uri.fromFile(photoFile))
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes[0]
                    val barcodeValue = barcode.rawValue ?: "No barcode found"
                    fetchBarcodeInfo(barcodeValue)
                } else {
                    Log.e("BarcodeRecognition", "No barcode found")
                }
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeRecognition", "Barcode scanning failed", e)
            }
    }

    private fun fetchBarcodeInfo(barcode: String) {
        val apiKey = getEnvVariable("GO_UPC_API_KEY")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://go-upc.com/api/v1/code/$barcode?key=$apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GoUPC", "Failed to get response from Go-UPC API", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    Log.d("GoUPC Response", responseBody)
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val productName = jsonResponse.getString("product")
                        runOnUiThread {
                            barcodeTextView?.text = productName
                            barcodeTextView?.visibility = View.VISIBLE
                        }
                    } catch (e: JSONException) {
                        Log.e("GoUPC", "Failed to parse response from Go-UPC API", e)
                    }
                } ?: run {
                    Log.e("GoUPC", "Empty response from Go-UPC API")
                }
            }
        })
    }

    private fun resetToInitialView() {
        imageView?.visibility = View.GONE
        barcodeTextView?.visibility = View.GONE
        isImageDisplayed = false
        initializeCamera()
    }

    private fun getEnvVariable(key: String): String {
        return BuildConfig::class.java.getField(key).get(null) as String
    }
}
