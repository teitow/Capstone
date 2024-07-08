package com.ktm.capstone

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter

class FeaturesPagerAdapter(private val mContext: Context) : PagerAdapter() {
    private val imageIds = intArrayOf(
        R.drawable.object_recognition,
        R.drawable.text_to_speech,
        R.drawable.weather_recognition,
        R.drawable.barcode_recognition,
        R.drawable.color_recognition,
        R.drawable.config
    )
    private val descriptions = arrayOf(
        "객체 인식",
        "텍스트 음성 변환",
        "날씨 확인",
        "바코드 스캔",
        "색상 인식",
        "앱 환경 설정"
    )

    override fun getCount(): Int {
        return imageIds.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        // 이미지와 텍스트를 포함하는 레이아웃을 생성합니다.
        val layout = LinearLayout(mContext)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 이미지뷰를 생성하고 레이아웃에 추가합니다.
        val imageView = ImageView(mContext)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(imageIds[position])
        layout.addView(imageView)

        // 텍스트뷰를 생성하고 레이아웃에 추가합니다.
        val textView = TextView(mContext)
        textView.text = descriptions[position]
        textView.textSize = 50f // 텍스트 크기를 24sp로 설정
        textView.setTypeface(null, Typeface.BOLD) // 텍스트를 굵게 설정
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.setTextColor(Color.BLACK) // 텍스트 색상을 흰색으로 설정
        textView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, -200, 0, 0) // 상단 마진을 -200dp로 설정하여 텍스트를 이미지와 더 가깝게 위치
        }
        layout.addView(textView)

        container.addView(layout, 0)
        return layout
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    fun getDescription(position: Int): String {
        return descriptions[position]
    }
}
