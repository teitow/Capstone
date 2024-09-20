package com.ktm.capstone

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OptionsAdapter(
    private val options: List<String>,
    private val isDarkMode: Boolean
) : RecyclerView.Adapter<OptionsAdapter.OptionViewHolder>() {

    private var selectedPosition = 0

    inner class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val optionTextView: TextView = itemView.findViewById(R.id.optionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_option, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.optionTextView.text = options[position]

        // 선택된 항목의 배경색을 각 모드에 맞는 색상으로 변경
        if (position == selectedPosition) {
            holder.optionTextView.setBackgroundColor(getSelectedColorForOption(options[position]))
        } else {
            // 선택되지 않은 항목은 다크 모드에 따라 색상 설정
            holder.optionTextView.setBackgroundColor(if (isDarkMode) Color.BLACK else Color.WHITE)
        }

        holder.optionTextView.setTextColor(
            if (isDarkMode) Color.WHITE else Color.BLACK
        )

        holder.optionTextView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousPosition) // 이전 선택된 항목 갱신
            notifyItemChanged(position)         // 현재 선택된 항목 갱신
        }
    }

    override fun getItemCount(): Int {
        return options.size
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    // 선택된 옵션에 따라 배경색을 변경
    private fun getSelectedColorForOption(option: String): Int {
        return when (option) {
            "메뉴 색상 변경" -> Color.parseColor("#00BCD4") // 메뉴 색상 변경: 청록색
            "배터리 세이브" -> Color.parseColor("#FF4081") // 배터리 세이브: 연두색
            "날씨 모드" -> Color.parseColor("#ffd700") // 날씨 모드 색상
            "객체 모드" -> Color.parseColor("#3b48b0") // 객체 모드 색상
            "바코드 모드" -> Color.parseColor("#ff8c00") // 바코드 모드 색상
            "색상 모드" -> Color.parseColor("#9966cc") // 색상 모드 색상

            else -> Color.MAGENTA // 기본 선택된 색상
        }
    }

}
