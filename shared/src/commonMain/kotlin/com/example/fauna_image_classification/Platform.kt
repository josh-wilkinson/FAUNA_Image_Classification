package com.example.fauna_image_classification

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform