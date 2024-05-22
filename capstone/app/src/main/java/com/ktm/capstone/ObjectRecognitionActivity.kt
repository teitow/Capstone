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
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
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

        supportActionBar?.title = "문자 인식"
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
                    analyzeImageWithCloudVision(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                    speak("사진 촬영에 실패했습니다.", "ID_ERROR")
                }
            })
    }

    private fun analyzeImageWithCloudVision(photoFile: File) {
        try {
            val inputStream = FileInputStream(photoFile)
            val imageBytes = ByteString.readFrom(inputStream)
            val image = Image.newBuilder().setContent(imageBytes).build()

            val feature = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build()
            val request = AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build()
            val requests = listOf(request)

            val credentialsStream = resources.openRawResource(R.raw.service_account_key)
            val credentials = GoogleCredentials.fromStream(credentialsStream)
            val settings = ImageAnnotatorSettings.newBuilder().setCredentialsProvider { credentials }.build()
            val client = ImageAnnotatorClient.create(settings)

            val response = client.batchAnnotateImages(requests)
            val labelAnnotations = response.responsesList[0].labelAnnotationsList

            if (labelAnnotations.isEmpty()) {
                speak("이미지에서 상황을 감지할 수 없습니다. 다시 시도해 주세요.", "ID_ERROR")
            } else {
                val descriptions = labelAnnotations.joinToString(", ") { it.description }
                ocrTextView?.text = descriptions
                ocrTextView?.visibility = View.VISIBLE
                generateDescription(descriptions)
            }
        } catch (e: IOException) {
            Log.e("CloudVision", "Failed to analyze image: " + e.message, e)
            speak("이미지 분석에 실패했습니다.", "ID_ERROR")
        }
    }

    private fun generateDescription(labels: String) {
        val apiKey = ""  // API 키 설정
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val json = JSONObject().apply {
            put("model", "gpt-3.5-turbo")  // 최신 모델로 변경
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a helpful assistant that describes scenes in Korean based on given labels.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "다음 라벨들을 사용하여 이 사진이 묘사하는 내용을 한 문장으로 간략하게 설명해 주세요: $labels")
                })
            })
        }


        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatGPT", "Failed to get response from ChatGPT API", e)
                speak("문장 생성에 실패했습니다.", "ID_ERROR")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    Log.d("ChatGPT Response", responseBody)  // 응답 전체를 로그에 출력
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.has("choices")) {
                            val generatedText = jsonResponse
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim()

                            runOnUiThread {
                                speak(generatedText, "ID_TEXT_READ")
                            }
                        } else {
                            Log.e("ChatGPT", "No choices found in response")
                            speak("문장 생성에 실패했습니다.", "ID_ERROR")
                        }
                    } catch (e: JSONException) {
                        Log.e("ChatGPT", "Failed to parse response from ChatGPT API", e)
                        speak("문장 생성에 실패했습니다.", "ID_ERROR")
                    }
                } ?: run {
                    speak("문장 생성에 실패했습니다.", "ID_ERROR")
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
