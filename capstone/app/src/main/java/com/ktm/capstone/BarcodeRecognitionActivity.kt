package com.ktm.capstone

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class BarcodeRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var isTTSInitialized = false
    private var cameraClickSound: MediaPlayer? = null
    private var imageView: ImageView? = null
    private var barcodeTextView: TextView? = null
    private var gestureDetector: GestureDetector? = null
    private var isImageDisplayed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var detectedBarcode: String? = null
    private var isBarcodeDetected = false
    private var lastDetectionTime: Long = 0
    private var isBarcodeDetectionActive = true
    private var yStart = 0f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("바코드를 인식하세요. 더블 탭하여 사진을 찍어 바코드를 분석할 수 있습니다.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_recognition)

        supportActionBar?.title = "바코드 인식"
        tts = TextToSpeech(this, this)
        imageView = findViewById(R.id.imageView)
        barcodeTextView = findViewById(R.id.barcodeTextView)
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click)
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isImageDisplayed) {
                    resetToInitialView()
                } else {
                    captureAndAnalyze()
                }
                return true
            }
        })
        initializeCamera()
    }

    private fun speak(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
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

                startBarcodeDetection()

            } catch (e: Exception) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startBarcodeDetection() {
        val analysisUseCase = ImageAnalysis.Builder()
            .build()

        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(this), { imageProxy ->
            if (isBarcodeDetectionActive) {
                processImageProxy(imageProxy)
            } else {
                imageProxy.close()
            }
        })

        cameraProvider?.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector!!,
            analysisUseCase
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val currentTime = System.currentTimeMillis()
                    if (barcodes.isEmpty()) {
                        if (isBarcodeDetected && currentTime - lastDetectionTime > 1000) {
                            isBarcodeDetected = false
                            speak("바코드가 감지되지 않습니다.", "ID_BARCODE_LOST")
                        }
                    } else {
                        for (barcode in barcodes) {
                            barcode.rawValue?.let {
                                if (!isBarcodeDetected) {
                                    detectedBarcode = it
                                    isBarcodeDetected = true
                                    speak("바코드가 감지되었습니다. 더블 탭하여 사진을 찍어 바코드를 분석하세요.", "ID_BARCODE_DETECTED")
                                }
                                lastDetectionTime = currentTime
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeDetection", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun captureAndAnalyze() {
        isBarcodeDetectionActive = false
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
                    fetchBarcodeInfo(detectedBarcode!!)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                }
            })
    }

    private fun getEnvVariable(key: String): String {
        return BuildConfig::class.java.getField(key).get(null) as String
    }

    private fun fetchBarcodeInfo(barcode: String) {
        val apiKey = getEnvVariable("RAPIDAPI_KEY")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://go-upc.p.rapidapi.com/v1/code/$barcode")
            .addHeader("X-RapidAPI-Key", apiKey)
            .addHeader("X-RapidAPI-Host", "go-upc.p.rapidapi.com")
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
                        val product = jsonResponse.getJSONObject("product")
                        val productName = product.getString("name")
                        val productDescription = product.getString("description")

                        runOnUiThread {
                            barcodeTextView?.text = "Name: $productName\nDescription: $productDescription"
                            barcodeTextView?.visibility = View.VISIBLE
                            speak("제품명: $productName. 설명: $productDescription", "ID_PRODUCT_INFO")
                            provideFurtherInstructions()
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

    private fun provideFurtherInstructions() {
        speak("다른 문장을 읽고 싶다면 화면을 두 번 누르세요. 메인 화면으로 돌아가고 싶다면 화면을 상하로 슬라이드 해주세요.", "ID_GUIDANCE")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.y
        } else if (action == MotionEvent.ACTION_UP) {
            val yEnd = event.y
            if (abs((yEnd - yStart).toDouble()) > 100) {
                navigateToMainActivity()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun resetToInitialView() {
        isBarcodeDetectionActive = true
        imageView?.visibility = View.GONE
        barcodeTextView?.visibility = View.GONE
        isImageDisplayed = false
        speak("바코드를 인식하세요. 더블 탭하여 사진을 찍어 바코드를 분석할 수 있습니다.", "ID_INITIAL")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
