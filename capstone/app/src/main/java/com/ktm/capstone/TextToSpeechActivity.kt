package com.ktm.capstone

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
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
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
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
    private var koreanRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private var latinRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
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
        koreanRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
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
            object : OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    cameraClickSound?.start()
                    val savedUri = Uri.fromFile(photoFile)
                    imageView?.setImageURI(savedUri)
                    imageView?.visibility = View.VISIBLE
                    isImageDisplayed = true
                    Log.d("CameraXApp", "Photo saved successfully: $savedUri")
                    try {
                        processImage(savedUri)
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

    private fun processImage(imageUri: Uri) {
        val croppedBitmap = cropImage(imageUri)
        if (croppedBitmap != null) {
            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
            processTextRecognition(inputImage)
        } else {
            speak("이미지 크롭에 실패했습니다.", "ID_ERROR")
        }
    }

    private fun processTextRecognition(inputImage: InputImage) {
        koreanRecognizer?.process(inputImage)
            ?.addOnSuccessListener { visionText: Text ->
                val filteredText = filterRecognizedText(visionText.text)
                if (filteredText.isEmpty()) {
                    speak("문자가 없습니다. 다시 찍으려면 화면을 두 번 누르세요.", "ID_ERROR")
                } else {
                    runOnUiThread {
                        ocrTextView?.text = filteredText
                        ocrTextView?.visibility = View.VISIBLE
                        speak(filteredText, "ID_TEXT_READ")
                    }
                }
            }
            ?.addOnFailureListener { e: Exception ->
                Log.e("TextRecognition", "Failed to process image: " + e.message, e)
                speak("텍스트 인식에 실패했습니다.", "ID_ERROR")
            }
    }

    private fun filterRecognizedText(text: String): String {
        val lines = text.lines()
        val filteredLines = lines.filter { line ->
            line.isNotEmpty() && // 빈 줄 제외
                    line.any { it.isLetterOrDigit() } && // 특수 문자나 공백만 있는 줄 제외
                    !line.all { it.isWhitespace() || !it.isLetterOrDigit() } // 공백이나 특수 문자만 있는 줄 제외
        }
        return filteredLines.joinToString("\n")
    }

    private fun cropImage(imageUri: Uri): Bitmap? {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
            val width = bitmap.width
            val height = bitmap.height
            val newWidth = (width * 0.9).toInt() // 크롭할 너비
            val newHeight = (height * 0.65).toInt() // 크롭할 높이
            val startX = (width - newWidth) / 2 // 크롭할 시작 x좌표
            val startY = (height - newHeight) / 2 // 크롭할 시작 y좌표

            return Bitmap.createBitmap(bitmap, startX, startY, newWidth, newHeight)
        } catch (e: IOException) {
            Log.e("CropImage", "Failed to crop image: " + e.message, e)
            return null
        }
    }

    private fun resetToInitialView() {
        imageView?.visibility = View.GONE
        ocrTextView?.visibility = View.GONE
        isImageDisplayed = false
        startCameraPreview()
        speak("초기 화면으로 돌아갑니다.", "ID_RESET")
    }

    private fun startCameraPreview() {
        cameraProvider?.let {
            it.unbindAll()
            camera = it.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector!!,
                preview,
                imageCapture
            )
            val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        }
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

    override fun onDestroy() {
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }
}
