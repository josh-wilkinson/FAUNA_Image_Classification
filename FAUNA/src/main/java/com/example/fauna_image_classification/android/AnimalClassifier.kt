package com.example.fauna_image_classification.android

import android.graphics.Bitmap

interface AnimalClassifier {
    fun classify(bitmap: Bitmap, rotation: Int): List<Classification>
}