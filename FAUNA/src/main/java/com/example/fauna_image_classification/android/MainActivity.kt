package com.example.fauna_image_classification.android

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity

var camera: Button? = null
var gallery: Button? = null
var imageView: ImageView? = null
var result: TextView? = null
var imageSize = 150

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        camera = findViewById(R.id.button)
        gallery = findViewById(R.id.button2)

        result = findViewById(R.id.result)
        imageView = findViewById(R.id.imageView)



    }
}



/*
@Composable
fun GreetingView(phrases: List<String>) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(phrases) { phrase ->
            Text(phrase)
            Divider()
        }
    }
}

@Composable
private fun DefaultPreview() {
    MyApplicationTheme {
        GreetingView(listOf("Hello, Android!"))
    }
}
*/