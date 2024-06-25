package com.ktm.capstone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import okhttp3.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.*
import kotlin.math.abs

class BarcodeRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTTSInitialized = false
    private lateinit var gestureDetector: GestureDetector
    private lateinit var resultTextView: TextView
    private lateinit var viewFinder: PreviewView
    private var yStart = 0f
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("화면을 두 번 누르면 바코드 인식이 시작됩니다. 카메라를 움직이다 바코드에 맞추면 자동으로 인식됩니다.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_recognition)

        tts = TextToSpeech(this, this)
        resultTextView = findViewById(R.id.resultTextView)
        viewFinder = findViewById(R.id.viewFinder)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                startBarcodeScanner()
                return true
            }
        })
        setupTTS()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("BarcodeRecognition", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startBarcodeScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
        integrator.setPrompt("바코드를 스캔하세요")
        integrator.setCameraId(0)  // Use a specific camera of the device
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                speak("바코드 스캔에 실패했습니다. 다시 시도하려면 화면을 두 번 누르세요.", "ID_ERROR")
            } else {
                fetchBarcodeInfo(result.contents)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun fetchBarcodeInfo(barcode: String) {
        val url = "https://gs1.koreannet.or.kr/pr/$barcode"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BarcodeAPI", "Failed to fetch data: ${e.message}")
                runOnUiThread {
                    speak("바코드 정보를 가져오는 데 실패했습니다.", "ID_ERROR")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        Log.d("BarcodeAPI", "Response Body: $responseBody")
                        val document = Jsoup.parse(responseBody)

                        // Extract product name from the main title
                        val productName = document.select("div.pv_title h3").first().text()

                        // Extract product category from the table, using exact match for "KAN 상품분류"
                        val productCategory = document.select("th:matchesOwn(^KAN 상품분류$) + td").first().text().split(" > ").last()

                        // Extract manufacturer from the table
                        val manufacturer = document.select("th:matchesOwn(^제조사/생산자$) + td").first().text()

                        val resultText = "제품명: $productName\n상품분류: $productCategory\n제조사: $manufacturer"
                        runOnUiThread {
                            resultTextView.text = resultText
                            speak(resultText, "ID_RESULT")
                        }
                    } catch (e: Exception) {
                        Log.e("BarcodeAPI", "Failed to parse response: ${e.message}")
                        runOnUiThread {
                            speak("바코드 정보를 처리하는 데 실패했습니다.", "ID_ERROR")
                        }
                    }
                } ?: run {
                    runOnUiThread {
                        speak("바코드 정보를 가져오는 데 실패했습니다.", "ID_ERROR")
                    }
                }
            }
        })
    }





    private fun speak(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun setupTTS() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Nothing to do here
            }

            override fun onDone(utteranceId: String) {
                if ("ID_RESULT" == utteranceId) {
                    tts?.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null)
                    speak("다시 스캔하려면 화면을 두 번 누르세요. 메인 화면으로 돌아가려면 화면을 상하로 슬라이드하세요.", "ID_GUIDANCE")
                }
            }

            override fun onError(utteranceId: String) {
                // Handle errors
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                speak("카메라 권한이 필요합니다.", "ID_PERMISSION")
                finish()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.y
        } else if (action == MotionEvent.ACTION_UP) {
            val yEnd = event.y
            if (abs(yEnd - yStart) > 100) {
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
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
}
