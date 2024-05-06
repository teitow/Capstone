package com.ktm.capstone

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.math.abs

class TextToSpeechActivity : AppCompatActivity(), OnInitListener {
    private var tts: TextToSpeech? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var recognizer: TextRecognizer? = null
    private var isTTSInitialized = false
    private var cameraClickSound: MediaPlayer? = null
    private var imageView: ImageView? = null
    private var gestureDetector: GestureDetector? = null
    private var yStart = 0f
    private var isImageDisplayed = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.")
            } else {
                isTTSInitialized = true
                speak("사진을 찍어주세요.", "ID_INITIAL")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_to_speech)
        tts = TextToSpeech(this, this)
        recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        imageView = findViewById(R.id.imageView)
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click)
        gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isImageDisplayed) {
                    captureAndAnalyze()
                } else {
                    resetToInitialView() // 초기 화면으로 리셋
                }
                return true
            }
        })
        initializeCamera()
        setupTTS()
    }

    private fun setupTTS() {
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // Nothing to do here
            }

            override fun onDone(utteranceId: String) {
                if ("ID_TEXT_READ" == utteranceId) {
                    tts!!.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null) // 1.5초 대기 후 메시지 재생
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
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun speakGuidance(text: String, utteranceId: String) {
        if (isTTSInitialized) {
            tts!!.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder().build()
                val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
                preview!!.setSurfaceProvider(viewFinder.getSurfaceProvider())
                imageCapture = ImageCapture.Builder().build()
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    (this as LifecycleOwner),
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
        imageView!!.visibility = View.GONE // 새 사진을 찍기 전에 ImageView를 숨깁니다.
        val fileName = "pic_" + System.currentTimeMillis() + ".jpg" // 타임스탬프를 이용해 고유한 파일 이름 생성
        val photoFile = File(getExternalFilesDir(null), fileName)
        val options = OutputFileOptions.Builder(photoFile).build()
        imageCapture!!.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    cameraClickSound!!.start()
                    val savedUri = Uri.fromFile(photoFile) // 새로 저장된 사진의 URI 사용
                    imageView!!.setImageURI(savedUri)
                    imageView!!.visibility = View.VISIBLE // 사진을 저장하고 ImageView를 다시 표시합니다.
                    isImageDisplayed = true // 이미지가 표시되었다고 상태 업데이트
                    Log.d("CameraXApp", "Photo saved successfully: $savedUri")
                    try {
                        processImage(InputImage.fromFilePath(this@TextToSpeechActivity, savedUri))
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: " + exception.message, exception)
                    speak("사진 촬영에 실패했습니다.", "ID_ERROR")
                }
            })
    }

    private fun processImage(image: InputImage) {
        recognizer!!.process(image)
            .addOnSuccessListener { visionText: Text ->
                if (visionText.text.trim { it <= ' ' }
                        .isEmpty()) {
                    speak("문자가 없습니다. 다시 찍으려면 화면을 두 번 누르세요.", "ID_ERROR")
                } else {
                    speak("인식된 텍스트: " + visionText.text, "ID_TEXT_READ")
                }
            }
            .addOnFailureListener { e: Exception ->
                Log.e("TextRecognition", "Failed to process image: " + e.message, e)
                speak("텍스트 인식에 실패했습니다.", "ID_ERROR")
            }
    }

    private fun resetToInitialView() {
        // 이미지 뷰를 숨김으로써 이미지가 보이지 않도록 설정
        imageView!!.visibility = View.GONE
        // 이미지 표시 상태를 false로 설정
        isImageDisplayed = false
        // 카메라 프리뷰를 재시작하여 사용자가 다시 사진을 찍을 수 있게 준비
        startCameraPreview()
        speak("초기 화면으로 돌아갑니다.", "ID_RESET")
    }

    private fun startCameraPreview() {
        // 모든 카메라 사용 사례를 해제
        cameraProvider!!.unbindAll()
        // 카메라 프리뷰를 다시 설정
        camera = cameraProvider!!.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector!!,
            preview,
            imageCapture
        )
        // 카메라 미리보기가 화면에 보이도록 설정
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        preview!!.setSurfaceProvider(viewFinder.getSurfaceProvider())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector!!.onTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.y
        } else if (action == MotionEvent.ACTION_UP) {
            val yEnd = event.y
            if (abs((yEnd - yStart).toDouble()) > 100) { // 슬라이드 거리가 100픽셀 이상이면 MainActivity로 이동
                navigateToMainActivity()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // 현재 액티비티 종료
    }

    override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }
}
