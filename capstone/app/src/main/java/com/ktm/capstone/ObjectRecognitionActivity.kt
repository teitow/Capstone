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
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import java.util.concurrent.ExecutionException

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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("사진을 찍어주세요. 메인 화면으로 가고 싶으시다면 위나 아래로 슬라이드 해주세요.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_recognition)

        supportActionBar?.title = "객체 인식"
        tts = TextToSpeech(this, this)
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
        initializeCamera()
        setupTTS()
    }

    private fun setupTTS() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Nothing to do here
            }

            override fun onDone(utteranceId: String) {
                if ("ID_TEXT_READ" == utteranceId) {
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
            } catch (e: ExecutionException) {
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
                    analyzeImageWithAzure(photoFile)
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

    private fun analyzeImageWithAzure(photoFile: File) {
        val apiKey = getEnvVariable("AZURE_VISION_API_KEY")
        val endpoint = getEnvVariable("AZURE_VISION_ENDPOINT")

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val mediaType = "application/octet-stream".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, photoFile)

        val request = Request.Builder()
            .url("$endpoint/vision/v3.1/describe")
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AzureVision", "Failed to get response from Azure Vision API", e)
                speak("이미지 분석에 실패했습니다.", "ID_ERROR")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    Log.d("AzureVision Response", responseBody)
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.has("description")) {
                            val descriptions = jsonResponse
                                .getJSONObject("description")
                                .getJSONArray("captions")
                                .getJSONObject(0)
                                .getString("text")

                            translateAndSpeak(descriptions)
                        } else {
                            Log.e("AzureVision", "No description found in response")
                            speak("문장 생성에 실패했습니다.", "ID_ERROR")
                        }
                    } catch (e: JSONException) {
                        Log.e("AzureVision", "Failed to parse response from Azure Vision API", e)
                        speak("문장 생성에 실패했습니다.", "ID_ERROR")
                    }
                } ?: run {
                    speak("문장 생성에 실패했습니다.", "ID_ERROR")
                }
            }
        })
    }

    private fun translateAndSpeak(text: String) {
        val translateApiKey = getEnvVariable("AZURE_TRANSLATE_API_KEY")
        val translateEndpoint = getEnvVariable("AZURE_TRANSLATE_ENDPOINT")

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = JSONArray().put(JSONObject().put("Text", text)).toString()
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$translateEndpoint/translate?api-version=3.0&from=en&to=ko")
            .addHeader("Ocp-Apim-Subscription-Key", translateApiKey)
            .addHeader("Ocp-Apim-Subscription-Region", "koreacentral")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Translate", "Failed to get response from Translate API", e)
                speak("번역에 실패했습니다.", "ID_ERROR")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val jsonResponse = JSONArray(responseBody)
                        val translations = jsonResponse.getJSONObject(0).getJSONArray("translations")
                        val translatedText = translations.getJSONObject(0).getString("text")

                        runOnUiThread {
                            ocrTextView?.text = translatedText
                            ocrTextView?.visibility = View.VISIBLE
                            speak(translatedText, "ID_TEXT_READ")
                        }
                    } catch (e: JSONException) {
                        Log.e("Translate", "Failed to parse response from Translate API", e)
                        speak("번역에 실패했습니다.", "ID_ERROR")
                    }
                } ?: run {
                    speak("번역에 실패했습니다.", "ID_ERROR")
                }
            }
        })
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
        speak("사진을 찍어주세요. 메인 화면으로 가고 싶으시다면 위나 아래로 슬라이드 해주세요.", "ID_INITIAL")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
