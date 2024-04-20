package com.ktm.capstone;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class TextToSpeechActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private Camera camera;
    private Preview preview;
    private ImageCapture imageCapture;
    private TextRecognizer textRecognizer;
    private PreviewView viewFinder;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_to_speech);

        if (allPermissionsGranted()) {
            viewFinder = findViewById(R.id.viewFinder);
            tts = new TextToSpeech(this, this);
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            gestureDetector = new GestureDetector(this, new GestureListener());
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 101);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported");
            } else {
                tts.speak("사진을 찍어주세요.", TextToSpeech.QUEUE_FLUSH, null, "TTS1");
            }
        } else {
            Log.e("TTS", "Initialization failed");
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            captureAndAnalyze();
            return super.onDoubleTap(e);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (Math.abs(e2.getY() - e1.getY()) > 100 && Math.abs(velocityY) > 100) {
                finish();  // Finish activity to return to MainActivity
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    private void captureAndAnalyze() {
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder().build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());
                textRecognizer.process(inputImage)
                        .addOnSuccessListener(visionText -> {
                            if (visionText.getText().trim().isEmpty()) {
                                tts.speak("문자가 없습니다. 다시 찍으려면 화면을 두 번 누르세요.", TextToSpeech.QUEUE_FLUSH, null, "TTS2");
                            } else {
                                tts.speak(visionText.getText(), TextToSpeech.QUEUE_FLUSH, null, "TTS3");
                            }
                            image.close();
                        })
                        .addOnFailureListener(e -> {
                            tts.speak("오류가 발생했습니다. 다시 시도해주세요.", TextToSpeech.QUEUE_FLUSH, null, "TTS4");
                            image.close();
                        });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                tts.speak("사진 촬영에 실패했습니다.", TextToSpeech.QUEUE_FLUSH, null, "TTS5");
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
