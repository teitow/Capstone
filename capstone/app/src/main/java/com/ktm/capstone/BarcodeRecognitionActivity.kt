package com.ktm.capstone

import android.Manifest
import android.content.Context
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
    private var mode: String = "BASIC"

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // SharedPreferences에서 TTS 설정 불러오기
            val prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
            val savedPitch = prefs.getFloat("pitch", 1.0f)
            val savedSpeed = prefs.getFloat("speed", 1.0f)

            tts?.let {
                it.language = Locale.KOREAN
                it.setPitch(savedPitch) // 저장된 피치 적용
                it.setSpeechRate(savedSpeed) // 저장된 속도 적용
            }

            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("화면을 두 번 누르면 바코드 인식이 시작됩니다. 세로로 스캔이 되니 물건의 바코드를 세로로 돌리거나 핸드폰을 가로로 돌려주세요. 카메라를 움직이다 바코드에 맞추면 자동으로 인식됩니다.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 다크 모드 설정 확인
        val themePref = getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = themePref.getBoolean("DARK_MODE", false)
        setTheme(isDarkMode)

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

        val sharedPref = getSharedPreferences("BarcodeModePref", Context.MODE_PRIVATE)
        mode = sharedPref.getString("MODE", "BASIC") ?: "BASIC"
    }

    private fun setTheme(isDarkMode: Boolean) {
        if (isDarkMode) {
            setContentView(R.layout.activity_barcode_recognition_dark)
        } else {
            setContentView(R.layout.activity_barcode_recognition)
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
                    resultTextView.text = "바코드 정보를 가져오는 데 실패했습니다."
                    resultTextView.visibility = TextView.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        Log.d("BarcodeAPI", "Response Body: $responseBody")
                        val document = Jsoup.parse(responseBody)

                        val productNameElement = document.selectFirst("div.pv_title h3")
                        val productCategoryElement = document.selectFirst("th:matchesOwn(^KAN 상품분류$) + td")
                        val manufacturerElement = document.selectFirst("th:matchesOwn(^제조사/생산자$) + td")
                        val sellerElement = document.selectFirst("th:matchesOwn(^판매자$) + td")
                        val dimensionsElement = document.selectFirst("th:matchesOwn(^규격\\(가로 x 세로 x 높이\\)$) + td")
                        val totalWeightElement = document.selectFirst("th:matchesOwn(^총중량$) + td")

                        if (productNameElement != null && productCategoryElement != null) {
                            val productName = productNameElement.text()
                            val productCategory = productCategoryElement.text().split(" > ").last()
                            val resultText = if (mode == "BASIC") {
                                "제품명: $productName\n상품분류: $productCategory"
                            } else {
                                val manufacturer = manufacturerElement?.text() ?: "정보 없음"
                                val seller = sellerElement?.text() ?: "정보 없음"
                                val dimensions = dimensionsElement?.text() ?: "정보 없음"
                                val totalWeight = totalWeightElement?.text() ?: "정보 없음"
                                "제품명: $productName\n상품분류: $productCategory\n제조사: $manufacturer\n판매자: $seller\n규격: $dimensions\n총중량: $totalWeight"
                            }
                            runOnUiThread {
                                resultTextView.text = resultText
                                resultTextView.visibility = TextView.VISIBLE
                                speak(resultText, "ID_RESULT")
                            }
                        } else {
                            runOnUiThread {
                                val errorMessage = "죄송합니다, 해당 상품을 조회할 수 없습니다."
                                resultTextView.text = errorMessage
                                resultTextView.visibility = TextView.VISIBLE
                                speak(errorMessage, "ID_NO_PRODUCT")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BarcodeAPI", "Failed to parse response: ${e.message}")
                        runOnUiThread {
                            speak("바코드 정보를 처리하는 데 실패했습니다.", "ID_ERROR")
                            resultTextView.text = "바코드 정보를 처리하는 데 실패했습니다."
                            resultTextView.visibility = TextView.VISIBLE
                        }
                    }
                } ?: run {
                    runOnUiThread {
                        speak("바코드 정보를 가져오는 데 실패했습니다.", "ID_ERROR")
                        resultTextView.text = "바코드 정보를 가져오는 데 실패했습니다."
                        resultTextView.visibility = TextView.VISIBLE
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
