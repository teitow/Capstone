package com.ktm.capstone;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        adapter = new FeaturesPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(0);  // Set initial item to the first one

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                readFeatureDescription(position % adapter.getCount());  // Use modulo to cycle descriptions
            }
        });

        // Load TTS settings from SharedPreferences
        prefs = getSharedPreferences("TTSConfig", MODE_PRIVATE);
        float savedPitch = prefs.getFloat("pitch", 1.0f);
        float savedSpeed = prefs.getFloat("speed", 1.0f);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                tts.setPitch(savedPitch);  // Apply saved pitch
                tts.setSpeechRate(savedSpeed);  // Apply saved speed
                tts.speak("화면을 슬라이드 하거나 탭을 하세요.", TextToSpeech.QUEUE_FLUSH, null, "InitialInstruction");
            }
        });

        gestureDetector = new GestureDetector(this, this);
        viewPager.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;  // Allow the ViewPager to handle the swipe
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
        int currentItem = viewPager.getCurrentItem();
        viewPager.setCurrentItem((currentItem + 1) % adapter.getCount(), true);  // Move to the next item, wrap around
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        int position = viewPager.getCurrentItem();
        Intent intent = null;
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
                // Handle potential errors or invalid positions
                break;
        }

        if (intent != null) {
            startActivity(intent);
        }
        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityY) > Math.abs(velocityX)) {
            if (velocityY > 0) {
                viewPager.setCurrentItem((viewPager.getCurrentItem() + 1) % adapter.getCount(), true);
            } else {
                int targetIndex = (viewPager.getCurrentItem() - 1) % adapter.getCount();
                if (targetIndex < 0) {
                    targetIndex += adapter.getCount();
                }
                viewPager.setCurrentItem(targetIndex, true);
            }
            return true;
        }
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
