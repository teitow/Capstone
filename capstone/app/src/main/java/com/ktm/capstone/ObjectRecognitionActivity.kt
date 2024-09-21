package com.ktm.capstone

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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

class ObjectRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var isTTSInitialized = false
    private var cameraClickSound: MediaPlayer? = null
    private var imageView: ImageView? = null
    private var ocrTextView: TextView? = null
    private var gestureDetector: GestureDetector? = null
    private var yStart = 0f
    private var isImageDisplayed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var descriptionMode: String = "BASIC"
    private lateinit var prefs: SharedPreferences
    private var isSimpleMode = false // 심플 모드 여부 확인 변수

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

                    // 모드에 따른 첫 메시지 출력
                    val initialMessage = if (isSimpleMode) {
                        "객체 인식 입니다."
                    } else {
                        "객체 인식 입니다. 더블 탭을 해 사진을 찍어주세요. 메인 화면으로 가고 싶으시다면 위나 아래로 슬라이드 해주세요."
                    }
                    speak(initialMessage, "ID_INITIAL")
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

        supportActionBar?.title = "객체 인식"
        tts = TextToSpeech(this, this)
        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE)
        imageView = findViewById(R.id.imageView)
        ocrTextView = findViewById(R.id.ocrTextView)
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

        // 현재 모드 확인 (심플 모드 여부 체크)
        val explainModePrefs = getSharedPreferences("ExplainModePref", MODE_PRIVATE)
        isSimpleMode = explainModePrefs.getString("CURRENT_MODE", "BASIC") == "SIMPLE"

        initializeCamera()
        setupTTS()

        descriptionMode = intent.getStringExtra("MODE") ?: "BASIC"
    }

    private fun setTheme(isDarkMode: Boolean) {
        if (isDarkMode) {
            setContentView(R.layout.activity_object_recognition_dark)
        } else {
            setContentView(R.layout.activity_object_recognition)
        }
    }


    private fun setupTTS() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Nothing to do here
            }

            override fun onDone(utteranceId: String) {
                if ("ID_TEXT_READ" == utteranceId && isSimpleMode) {
                    // 설명이 완료되었을 때 심플 모드에서 "설명이 완료되었습니다." 출력
                    speak("설명이 완료되었습니다.", "ID_COMPLETED")
                } else if ("ID_TEXT_READ" == utteranceId) {
                    // 베이직 모드에서만 추가 설명 제공
                    tts?.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null)
                    speakGuidance(
                        "다른 문장을 읽고 싶다면 화면을 두 번 누르세요. 메인 화면으로 돌아가고 싶다면 화면을 상하로 슬라이드 해주세요.",
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

    private fun speakGuidance(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
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
            } catch (e: Exception) {
                Log.e("CameraXApp", "Use case binding failed", e)
            } catch (e: InterruptedException) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        imageView?.visibility = View.GONE
        ocrTextView?.visibility = View.GONE
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
                    analyzeImageWithOpenAI(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                    speak("사진 촬영에 실패했습니다.", "ID_ERROR")
                }
            })
    }

    private fun getEnvVariable(key: String): String {
        return BuildConfig::class.java.getField(key).get(null) as String
    }

    private fun analyzeImageWithOpenAI(photoFile: File) {
        val apiKey = getEnvVariable("ORA_OPENAI_API_KEY")
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val base64Image = encodeImageToBase64(photoFile, 512, 512) // 이미지를 512x512로 리사이즈하여 Base64 인코딩
        val descriptionText = if (descriptionMode == "DETAILED") {
            "시각 장애인을 위해 이 이미지를 문자로 설명하고자 합니다. 섬세하고 상세하게 이 사진을 요약해주십시오."
        } else {
            "시각 장애인을 위해 이 이미지를 문자로 설명하고자 합니다. 핵심적인 부분만 간략하게 문자로 이 사진을 요약해주십시오."
        }

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
                            ocrTextView?.text = description
                            ocrTextView?.visibility = View.VISIBLE

                            // 설명 후 완료 메시지 출력
                            speak(description, "ID_TEXT_READ")
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
        val bitmap = BitmapFactory.decodeFile(photoFile.path)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
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
        imageView?.visibility = View.GONE
        ocrTextView?.visibility = View.GONE
        isImageDisplayed = false
        initializeCamera() // 카메라를 다시 초기화

        // 모드에 따른 초기 메시지
        val resetMessage = if (isSimpleMode) {
            "객체 인식 입니다. 사진을 찍어주세요."
        } else {
            "사진을 찍어주세요. 메인 화면으로 가고 싶으시다면 위나 아래로 슬라이드 해주세요."
        }

        speak(resetMessage, "ID_INITIAL")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
