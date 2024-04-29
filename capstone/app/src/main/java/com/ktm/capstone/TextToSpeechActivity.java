package com.ktm.capstone;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class TextToSpeechActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private Camera camera;
    private Preview preview;
    private ImageCapture imageCapture;
    private TextRecognizer recognizer;
    private boolean isTTSInitialized = false;
    private MediaPlayer cameraClickSound;
    private ImageView imageView;
    private GestureDetector gestureDetector;
    private float yStart = 0;

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Korean language is not supported.");
            } else {
                isTTSInitialized = true;
                speak("사진을 찍어주세요.", "ID_INITIAL");

            }
        } else {
            Log.e("TTS", "Initialization failed.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_to_speech);

        tts = new TextToSpeech(this, this);
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        imageView = findViewById(R.id.imageView);
        cameraClickSound = MediaPlayer.create(this, R.raw.camera_click);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                captureAndAnalyze();
                return true;
            }
        });

        initializeCamera();
        setupTTS();
    }

    private void setupTTS() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Nothing to do here
            }

            @Override
            public void onDone(String utteranceId) {
                if ("ID_TEXT_READ".equals(utteranceId)) {
                    tts.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, null); // 1.5초 대기 후 메시지 재생
                    speakGuidance("다른 문장을 읽고 싶다면 화면을 두 번 누르세요. 메인 화면으로 돌아가고 싶다면 화면을 상하로 슬라이드 해주세요.", "ID_GUIDANCE");
                }
            }

            @Override
            public void onError(String utteranceId) {
                // Handle errors
            }
        });
    }

    private void speak(String text, String utteranceId) {
        if (isTTSInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        }
    }

    private void speakGuidance(String text, String utteranceId) {
        if (isTTSInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
        }
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                preview = new Preview.Builder().build();
                PreviewView viewFinder = findViewById(R.id.viewFinder);
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndAnalyze() {
        File photoFile = new File(getExternalFilesDir(null), "pic.jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                cameraClickSound.start();
                Uri savedUri = outputFileResults.getSavedUri();
                imageView.setImageURI(savedUri);
                Log.d("CameraXApp", "Photo saved successfully");
                try {
                    processImage(InputImage.fromFilePath(TextToSpeechActivity.this, savedUri));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraXApp", "Photo capture failed: " + exception.getMessage(), exception);
                speak("사진 촬영에 실패했습니다.", "ID_ERROR");
            }
        });
    }

    private void processImage(InputImage image) {
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    if (visionText.getText().trim().isEmpty()) {
                        speak("문자가 없습니다. 다시 찍으려면 화면을 두 번 누르세요.", "ID_ERROR");
                    } else {
                        speak("인식된 텍스트: " + visionText.getText(), "ID_TEXT_READ");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("TextRecognition", "Failed to process image: " + e.getMessage(), e);
                    speak("텍스트 인식에 실패했습니다.", "ID_ERROR");
                });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            yStart = event.getY();
        } else if (action == MotionEvent.ACTION_UP) {
            float yEnd = event.getY();
            if (Math.abs(yEnd - yStart) > 100) { // 슬라이드 거리가 100픽셀 이상이면 메인 화면으로
                navigateToMainActivity();
            }
        }
        return super.onTouchEvent(event);
    }

    private void navigateToMainActivity() {
        speak("메인 화면으로 이동합니다.", "ID_NAVIGATE_MAIN");
        finish();
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
