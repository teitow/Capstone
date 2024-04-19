package com.ktm.capstone;

import android.net.Uri;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class CameraActivity extends Activity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                tts.speak("사진을 찍어주세요.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                Intent resultIntent = new Intent(this, TextToSpeechActivity.class);
                resultIntent.putExtra("data", imageUri);
                startActivity(resultIntent);
            } else {
                tts.speak("사진 촬영에 실패했습니다. 다시 시도해 주세요.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
            finish();
        }
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
