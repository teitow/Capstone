package com.ktm.capstone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
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
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ColorRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var camera: Camera? = null
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private var isTTSInitialized = false
    private var cameraClickSound: MediaPlayer? = null
    private lateinit var imageView: ImageView
    private lateinit var colorTextView: TextView
    private lateinit var gestureDetector: GestureDetector
    private var yStart = 0f
    private var isImageDisplayed = false
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private var descriptionMode: String = "BASIC"
    private lateinit var prefs: SharedPreferences

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                val result = it.setLanguage(Locale.KOREAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Korean language is not supported.")
                } else {
                    isTTSInitialized = true
                    val savedPitch = prefs.getFloat("pitch", 1.0f)
                    val savedSpeed = prefs.getFloat("speed", 1.0f)
                    it.setPitch(savedPitch)
                    it.setSpeechRate(savedSpeed)
                    speak("색상을 인식하려면 사진을 찍어주세요.", "ID_INITIAL")
                }
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

        // TTS 초기화
        tts = TextToSpeech(this, this)
        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        imageView = findViewById(R.id.imageView)
        colorTextView = findViewById(R.id.colorTextView)
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

        // ColorModePref에서 저장된 모드 읽기
        val modePrefs = getSharedPreferences("ColorModePref", Context.MODE_PRIVATE)
        descriptionMode = modePrefs.getString("MODE", "BASIC") ?: "BASIC" // 기본값으로 BASIC 설정
        Log.d("ColorRecognition", "Loaded mode: $descriptionMode") // 로드된 모드를 로그로 출력

        initializeCamera()
        setupTTS()
    }


    private fun setTheme(isDarkMode: Boolean) {
        if (isDarkMode) {
            setContentView(R.layout.activity_color_recognition_dark)
        } else {
            setContentView(R.layout.activity_color_recognition)
        }
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
            } catch (e: Exception) {
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
                if ("COLOR_DETECTED" == utteranceId) {
                    tts?.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null)
                    speak(
                        "색상을 다시 인식하려면 화면을 두 번 탭하세요. 메인 화면으로 돌아가려면 화면을 상하로 슬라이드하세요.",
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
        colorTextView.visibility = View.GONE
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
                    if (descriptionMode == "BASIC") {
                        analyzeColor(savedUri)
                    } else {
                        analyzeImageWithOpenAI(photoFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                    speak("사진 촬영에 실패했습니다.", "ID_ERROR")
                }
            })
    }

    private fun analyzeColor(imageUri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
            if (bitmap != null && !bitmap.isRecycled) {
                val centerX = bitmap.width / 2
                val centerY = bitmap.height / 2

                // 이미지 중앙의 픽셀 색상을 가져옵니다.
                val pixel = bitmap.getPixel(centerX, centerY)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)
                val colorName = getColorNameHSV(hsv[0], hsv[1], hsv[2])
                runOnUiThread {
                    colorTextView.text = "인식된 색상: $colorName"
                    colorTextView.visibility = View.VISIBLE
                    speak("인식된 색상은 $colorName 입니다.", "COLOR_DETECTED")
                }
                bitmap.recycle()
            }
        } catch (e: IOException) {
            Log.e("ColorRecognition", "Error accessing file: " + e.message, e)
        }
    }

    private fun analyzeImageWithOpenAI(photoFile: File) {
        val apiKey = getEnvVariable("ORA_OPENAI_API_KEY")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val base64Image = encodeImageToBase64(photoFile, 512, 512) // 이미지를 512x512로 리사이즈하여 Base64 인코딩
        val descriptionText = "시각 장애인을 위한 설명이 필요합니다, 지금 현재 카메라가 촬영하고 있는 물체중에 카메라에 가장 가까운 물체의 색상을 간략하게 서술해주세요."

        val json = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().put(JSONObject().apply {
                    put("type", "text")
                    put("text", descriptionText)
                }).put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Image")
                        put("detail", "low")
                    })
                }))
            }))
            put("max_tokens", 300)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OpenAI", "Failed to get response from OpenAI API", e)
                speak("이미지 분석에 실패했습니다.", "ID_ERROR")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    Log.d("OpenAI Response", responseBody)
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val choices = jsonResponse.getJSONArray("choices")
                        val description = choices.getJSONObject(0).getJSONObject("message").getString("content")

                        runOnUiThread {
                            colorTextView.text = description
                            colorTextView.visibility = View.VISIBLE
                            speak(description, "COLOR_DETECTED")
                        }
                    } catch (e: JSONException) {
                        Log.e("OpenAI", "Failed to parse response from OpenAI API", e)
                        speak("문장 생성에 실패했습니다.", "ID_ERROR")
                    }
                } ?: run {
                    speak("문장 생성에 실패했습니다.", "ID_ERROR")
                }
            }
        })
    }

    private fun encodeImageToBase64(photoFile: File, width: Int, height: Int): String {
        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, Uri.fromFile(photoFile))
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getColorNameHSV(hue: Float, saturation: Float, value: Float): String {
        if (value < 0.1) return "검정" // 명도가 매우 낮으면 검정색
        if (value > 0.8 && saturation < 0.2) return "흰색" // 명도가 높고 채도가 낮으면 흰색 (범위 확장)
        if (saturation < 0.1 && value < 0.8) return "회색" // 채도가 매우 낮고 명도가 매우 높지 않으면 회색
        if (hue < 20) return "빨강"
        if (hue < 40) return "주황"
        if (hue < 75) return "노랑"
        if (hue < 140) return "초록"
        if (hue < 160) return "청록색"
        if (hue < 220) return "파랑"
        if (hue < 240) return "남색"
        if (hue < 280) return "보라"
        return if (hue < 330) "핑크" else "색상을 알 수 없습니다. 다시 한번 촬영해주세요"
        // 일부 색상이 겹치거나 구분이 모호할 경우
    }

    private fun resetToInitialView() {
        imageView.visibility = View.GONE
        colorTextView.visibility = View.GONE
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
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }

    private fun getEnvVariable(key: String): String {
        return BuildConfig::class.java.getField(key).get(null) as String
    }
}
