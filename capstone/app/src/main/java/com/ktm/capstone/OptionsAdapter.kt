package com.ktm.capstone

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OptionsAdapter(
    private val options: List<String>
) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

    private var selectedPosition = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val optionTextView: TextView = view.findViewById(R.id.optionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.optionTextView.text = options[position]
        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(Color.parseColor("#FF4081"))
            holder.optionTextView.setTextColor(Color.WHITE)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.optionTextView.setTextColor(Color.BLACK)
        }
    }

    override fun getItemCount(): Int = options.size

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }
}
