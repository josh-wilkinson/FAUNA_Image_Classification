package com.example.fauna_image_classification.android

import android.annotation.SuppressLint

import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class Popup : AppCompatActivity() {

//var extras = intent.extras

    private val client = OkHttpClient()

    private val apiKey : String = "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"
    private var question : String = "How are you?"

    private var txtResponse : TextView? = null
    private var txtHeader : TextView? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //txtHeader = findViewById<TextView>(R.id.popupHeaderText) as TextView
        var extras = intent.extras
        if (extras != null){
            var classification = intent.getStringExtra("Classification")
            question = "$classification poisonous/toxic?"
            var confidence = intent.getStringExtra("maxConfidence")

            var header = "Classification: $classification, confidence: $confidence . \n\n Is it poisonous/toxic?"
            setContentView(R.layout.popup_window)

            txtResponse = findViewById<TextView>(R.id.popupTextView) as TextView
            txtHeader = findViewById<TextView>(R.id.popupHeaderText) as TextView

            val dm : DisplayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)

            var width = dm.widthPixels
            var height = dm.heightPixels

            // set size of window by multiplying the width and height
            window.setLayout(((width * 0.8).toInt()), ((height * 0.8).toInt()))


            getGPTResponse(question) { response ->
                runOnUiThread {
                    txtHeader?.setText(header)
                    txtResponse?.setText(response)
                }
            }
        }

    }

    fun getGPTResponse(question: String, callback: (String) -> Unit) {
        // the url of the open ai api website to access openAI model
        val url = "https://api.openai.com/v1/chat/completions"
        // the request body contains all the details for the openAI api call
        val requestBody="""
            {
                "model": "gpt-3.5-turbo",
                "messages": [{"role": "user", "content": "$question"}],
                "max_tokens": 512,
                "top_p": 1,
                "temperature": 0.5,
                "frequency_penalty": 0,
                "presence_penalty": 0
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ERROR", "Api call failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null) {
                    Log.v("data", body)
                }
                else {
                    Log.v("data", "empty")
                }
                val jsonObject = JSONObject(body)
                val jsonArray : JSONArray = jsonObject.getJSONArray("choices")
                val message : JSONObject = jsonArray.getJSONObject(0).getJSONObject("message")


                //val jsonObject2 : JSONObject = jsonArray.getJSONObject(1)
                //val textResult = jsonArray.getJSONObject(1).getString("content")
                val textResult = message.getString("content")

                callback(textResult)
            }
        })
        //callback("textResult")

    }

}
