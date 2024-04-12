package com.ktm.capstone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.util.Locale;
import androidx.viewpager.widget.ViewPager;

public class MainActivity extends Activity implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private ViewPager viewPager;
    private FeaturesPagerAdapter adapter;
    private TextToSpeech tts;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        adapter = new FeaturesPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // 페이지가 변경될 때마다 TTS로 현재 페이지의 기능 설명을 읽어줍니다.
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                readFeatureDescription(position);
            }
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
            }
        });

        gestureDetector = new GestureDetector(this, this);
        // 페이지를 스와이프 할 때와 탭 할 때 동일하게 처리하도록 설정
        viewPager.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;  // ViewPager가 스와이프 동작을 정상 처리하도록 false 반환
        });
    }

    private void readFeatureDescription(int position) {
        if (tts != null && adapter != null) {
            String description = adapter.getDescription(position);
            tts.speak(description, TextToSpeech.QUEUE_FLUSH, null, "FeatureDescription");
        }
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        // 페이지가 변경될 때 자동으로 읽기 때문에 여기서는 추가적인 작업이 필요 없습니다.
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        int position = viewPager.getCurrentItem();
        navigateToFeatureActivity(position);
        return true;
    }

    private void navigateToFeatureActivity(int position) {
        Intent intent;
        switch (position) {
            case 0:
                intent = new Intent(this, ObjectRecognitionActivity.class);
                break;
            case 1:
                intent = new Intent(this, TextToSpeechActivity.class);
                break;
            case 2:
                intent = new Intent(this, MoneyRecognitionActivity.class);
                break;
            case 3:
                intent = new Intent(this, BarcodeRecognitionActivity.class);
                break;
            case 4:
                intent = new Intent(this, UsageInstructionsActivity.class);
                break;
            case 5:
                intent = new Intent(this, VoiceSettingsActivity.class);
                break;
            default:
                return;
        }
        startActivity(intent);
    }

    // 필요한 GestureDetector의 다른 메소드들을 구현하세요.
    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }
}
