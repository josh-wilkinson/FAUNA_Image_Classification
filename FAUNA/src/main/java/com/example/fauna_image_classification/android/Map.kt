package com.example.fauna_image_classification.android

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class Map : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_view)

        val myWebView: WebView = findViewById(R.id.webview)
        myWebView.loadUrl("https://www.google.com")
    }

}