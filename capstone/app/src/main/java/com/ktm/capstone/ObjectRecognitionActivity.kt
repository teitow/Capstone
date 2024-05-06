package com.ktm.capstone

import android.Manifest
import android.graphics.Bitmap
import android.util.Size
import android.view.View
import android.widget.Button
import androidx.camera.core.Preview
import okhttp3.Call
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutionException

class ObjectRecognitionActivity : AppCompatActivity() {
    private var textureView: TextureView? = null
    private var captureButton: Button? = null
    private var imageCapture: ImageCapture? = null
    private var tts: TextToSpeech? = null
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_recognition)

        // 권한 검사
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.CAMERA),
                100
            )
        }
        textureView = findViewById<TextureView>(R.id.textureView)
        captureButton = findViewById<Button>(R.id.button_capture)
        setupCamera()

        // TextToSpeech 초기화
        tts = TextToSpeech(this, OnInitListener { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                val result: Int = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(
                        getApplicationContext(),
                        "TTS: Language not supported",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    getApplicationContext(),
                    "TTS: Initialization failed!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        captureButton!!.setOnClickListener { view: View? -> captureImage() }
    }

    private fun setupCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: ExecutionException) {
                Toast.makeText(this, "Error starting camera: " + e.message, Toast.LENGTH_SHORT)
                    .show()
            } catch (e: InterruptedException) {
                Toast.makeText(this, "Error starting camera: " + e.message, Toast.LENGTH_SHORT)
                    .show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(textureView.getOutlineProvider() as SurfaceProvider)
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(640, 480))
            .build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
    }

    private fun captureImage() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap: Bitmap = textureView.getBitmap()
                    sendImageToServer(bitmap)
                    image.close() // ImageProxy는 close()로 닫아야 합니다.
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@ObjectRecognitionActivity,
                        "Capture failed: " + exception.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun sendImageToServer(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        val client = OkHttpClient()
        val requestBody: RequestBody = RequestBody.create(MediaType.parse("image/png"), byteArray)
        val request: Request = Builder()
            .url("http://127.0.0.1:5000/detect")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback() {
            fun onFailure(call: Call, e: IOException) {
                runOnUiThread(Runnable {
                    Toast.makeText(
                        getApplicationContext(),
                        "Request failed: " + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                })
            }

            @Throws(IOException::class)
            fun onResponse(call: Call, response: Response) {
                val responseData: String = response.body().string()
                try {
                    val json = JSONObject(responseData)
                    val detectedObject: String = json.getJSONObject("detection").getString("label")
                    speak("Detected object is: $detectedObject")
                } catch (e: Exception) {
                    runOnUiThread(Runnable {
                        Toast.makeText(
                            getApplicationContext(),
                            "Failed to parse response",
                            Toast.LENGTH_SHORT
                        ).show()
                    })
                }
            }
        })
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    protected override fun onDestroy() {
        if (tts != null) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
