package com.ktm.capstone

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.math.abs

class BarcodeRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var camera: Camera? = null
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private var isTTSInitialized = false
    private var cameraClickSound: MediaPlayer? = null
    private lateinit var imageView: ImageView
    private lateinit var barcodeTextView: TextView
    private lateinit var gestureDetector: GestureDetector
    private var yStart = 0f
    private var isImageDisplayed = false
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("바코드를 스캔하려면 사진을 찍어주세요.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_recognition)
        tts = TextToSpeech(this, this)
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
        setupTTS()
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder().build()
                val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
                imageCapture = ImageCapture.Builder().build()
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    (this as LifecycleOwner),
                    cameraSelector,
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

    private fun setupTTS() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Nothing to do here
            }

            override fun onDone(utteranceId: String) {
                if ("BARCODE_DETECTED" == utteranceId) {
                    tts?.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null)
                    speak(
                        "바코드를 다시 스캔하려면 화면을 두 번 탭하세요. 메인 화면으로 돌아가려면 화면을 상하로 슬라이드하세요.",
                        "ID_GUIDANCE"
                    )
                }
            }

            override fun onError(utteranceId: String) {
                // Handle errors
            }
        })
    }

    private fun speak(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun captureAndAnalyze() {
        imageView.visibility = View.GONE
        barcodeTextView.visibility = View.GONE
        val fileName = "pic_" + System.currentTimeMillis() + ".jpg"
        val photoFile = File(getExternalFilesDir(null), fileName)
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    cameraClickSound?.start()
                    val savedUri = Uri.fromFile(photoFile)
                    imageView.setImageURI(savedUri)
                    imageView.visibility = View.VISIBLE
                    isImageDisplayed = true
                    Log.d("CameraXApp", "Photo saved successfully: $savedUri")
                    analyzeBarcode(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                    speak("사진 촬영에 실패했습니다.", "ID_ERROR")
                }
            })
    }

    private fun analyzeBarcode(imageUri: Uri) {
        // 바코드 분석 및 제품 정보 가져오기
        val barcode = getBarcodeFromImage(imageUri) // 이미지에서 바코드 인식하는 함수 호출
        if (barcode != null) {
            fetchProductInfo(barcode)
        } else {
            speak("바코드를 인식할 수 없습니다. 다시 시도해주세요.", "ID_ERROR")
        }
    }

    private fun getBarcodeFromImage(imageUri: Uri): String? {
        // 이미지에서 바코드를 인식하는 로직을 구현합니다.
        // 여기서는 바코드 인식을 간략화하여 바코드를 반환하는 예시입니다.
        return "1234567890123" // 실제로는 이미지에서 바코드를 인식한 값을 반환해야 합니다.
    }

    private fun fetchProductInfo(barcode: String) {
        val apiKey = BuildConfig.BARCODE_API_KEY
        val apiUrl = "https://api.data.go.kr/openapi/barcodeinfo?serviceKey=$apiKey&barcode=$barcode"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    speak("제품 정보를 가져오는데 실패했습니다.", "ID_ERROR")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val productInfo = parseProductInfo(responseBody)
                    runOnUiThread {
                        displayProductInfo(productInfo)
                    }
                } else {
                    runOnUiThread {
                        speak("제품 정보를 가져오는데 실패했습니다.", "ID_ERROR")
                    }
                }
            }
        })
    }

    private fun parseProductInfo(responseBody: String?): String {
        responseBody?.let {
            val json = JSONObject(it)
            val productName = json.getString("productName")
            val manufacturerName = json.getString("manufacturerName")
            val origin = json.getString("origin")
            val price = json.getString("price")
            return "제품명: $productName\n제조사: $manufacturerName\n원산지: $origin\n가격: $price"
        }
        return "제품 정보를 찾을 수 없습니다."
    }

    private fun displayProductInfo(info: String) {
        barcodeTextView.text = info
        barcodeTextView.visibility = View.VISIBLE
        speak(info, "BARCODE_DETECTED")
    }

    private fun resetToInitialView() {
        imageView.visibility = View.GONE
        barcodeTextView.visibility = View.GONE
        isImageDisplayed = false
        startCameraPreview()
        speak("초기 화면으로 돌아갑니다.", "ID_RESET")
    }

    private fun startCameraPreview() {
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            preview,
            imageCapture
        )
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        preview.setSurfaceProvider(viewFinder.surfaceProvider)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
