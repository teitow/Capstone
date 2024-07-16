package com.ktm.capstone

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.viewpager.widget.PagerAdapter

class FeaturesPagerAdapter(private val mContext: Context) : PagerAdapter() {
    private val imageIdsLight = intArrayOf(
        R.drawable.object_recognition,
        R.drawable.text_to_speech,
        R.drawable.weather_recognition,
        R.drawable.barcode_recognition,
        R.drawable.color_recognition,
        R.drawable.config
    )

    private val imageIdsDark = intArrayOf(
        R.drawable.object_recognition_dark,
        R.drawable.text_to_speech_dark,
        R.drawable.weather_recognition_dark,
        R.drawable.barcode_recognition_dark,
        R.drawable.color_recognition_dark,
        R.drawable.config_dark
    )

    override fun getCount(): Int {
        return imageIdsLight.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = LinearLayout(mContext)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 다크 모드 여부에 따라 배경색을 설정
        val sharedPref = mContext.getSharedPreferences("ThemePref", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("DARK_MODE", false)
        val imageIds = if (isDarkMode) imageIdsDark else imageIdsLight

        layout.setBackgroundColor(if (isDarkMode) Color.BLACK else Color.WHITE)

        val imageView = ImageView(mContext)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(imageIds[position])
        layout.addView(imageView)

        container.addView(layout, 0)
        return layout
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    fun getDescription(position: Int): String {
        val descriptions = arrayOf(
            "객체 인식",
            "텍스트 음성 변환",
            "날씨 확인",
            "바코드 스캔",
            "색상 인식",
            "앱 환경 설정"
        )
        return descriptions[position]
    }
}
