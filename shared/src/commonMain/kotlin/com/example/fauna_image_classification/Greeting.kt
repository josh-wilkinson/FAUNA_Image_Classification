package com.example.fauna_image_classification

class Greeting {
    private val platform: Platform = getPlatform()

    fun greet(): String {
        return "Hello Multiplatform, ${platform.name}!"
    }
}