package com.example.fauna_image_classification.android

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SliderItemViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {

    val tvItem: TextView? = itemView?.findViewById(R.id.tv_item)
}