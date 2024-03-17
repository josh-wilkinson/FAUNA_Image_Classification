package com.example.fauna_image_classification.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Feedback : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.feedback_window)

        var nameText = findViewById<EditText>(R.id.edit1) as EditText
        var feedbackText = findViewById<EditText>(R.id.edit2) as EditText
        var submitButton = findViewById<Button>(R.id.feedbackButton) as Button

        // set filtering parameters here TODO
        submitButton?.setOnClickListener(View.OnClickListener {

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "message/html"
            intent.putExtra(Intent.EXTRA_EMAIL, "fauna.appaddress@gmail.com")
            intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback from App")
            intent.putExtra(Intent.EXTRA_TEXT, "Name: " + nameText.text + "\n Message: " + feedbackText.text)

            try {
                startActivity(Intent.createChooser(intent, "Please select an Email"))
            }
            catch (e : android.content.ActivityNotFoundException) {
                Toast.makeText(this, "There are no Email Clients", Toast.LENGTH_SHORT).show()
            }

        })
    }


}