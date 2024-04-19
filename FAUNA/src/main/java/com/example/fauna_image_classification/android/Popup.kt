package com.example.fauna_image_classification.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Firebase
import com.google.firebase.database.database
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
import java.net.URL
import java.util.concurrent.TimeUnit


data class CSVData(
    val type: String,
    val lon: Double,
    val lat: Double
)

class Popup : AppCompatActivity(), LocationListener {

//var extras = intent.extras

    private val client = OkHttpClient()
        .newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey : String = "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"

    private lateinit var bitmapOutput : Bitmap
    private lateinit var imageView : ImageView
    lateinit var locationManager: LocationManager
    private lateinit var locationByGps : Location

    private var fusedLocationProviderClient : FusedLocationProviderClient? = null

    private var type : String? = null
    private var classification : String? = null
    private var longitude: Double? = null
    private var latitude: Double? = null

    private var currentLocation: Location? = null
    private var txtResponse : TextView? = null
    private var txtHeader : TextView? = null

    private var moreInfoButton : Button? = null
    private var askMeButton : Button? = null

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var extras = intent.extras
        if (extras != null){

            classification = intent.getStringExtra("Classification")

            type = intent.getStringExtra("Type")

            var question = "$classification poisonous/toxic?"
            var prompt = "$classification $type"

            var confidence = intent.getStringExtra("maxConfidence")

            var header = "Classification: $classification, confidence: $confidence."
            setContentView(R.layout.popup_window)

            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

            // update longitude and latitude
            try {
                getLocation()
            }
            catch (e : Exception) {
                e.printStackTrace()
            }

            // chat GPT stuff
            txtResponse = findViewById<TextView>(R.id.popupTextView) as TextView

            txtHeader = findViewById<TextView>(R.id.popupHeaderText) as TextView
            imageView = findViewById<ImageView>(R.id.popupImageView) as ImageView
            moreInfoButton = findViewById<Button>(R.id.popupMoreInfoButton) as Button
            askMeButton = findViewById<Button>(R.id.popupAskMeButton) as Button

            txtResponse?.movementMethod = ScrollingMovementMethod()

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
/*
            generateDALLEImage(prompt) { response ->
                runOnUiThread {
                    imageView.setImageBitmap(response)
                }
            }*/

            moreInfoButton?.setOnClickListener(View.OnClickListener {
                moreInfoButton?.visibility = View.GONE
                txtResponse?.visibility = View.VISIBLE
            })

            askMeButton?.setOnClickListener(View.OnClickListener {

                val intent = Intent(this, Chat::class.java)
                startActivity(intent)

            })

        }

    }

    private fun addDataToDB(type: String, lon: Double, lat: Double) {
        // create a hashmap of the data
        var dataHashmap : HashMap<String, String> = HashMap();

        dataHashmap.put("name", "$classification")
        dataHashmap.put("type", "$type")
        dataHashmap.put("lon", "$lon")
        dataHashmap.put("lat", "$lat")

        // Write a message to the database
        val database = Firebase.database
        val myRef = database.getReference("map")

        val key = myRef.push().key as String
        dataHashmap.put("key", "$key")

        myRef.child(key).setValue(dataHashmap).addOnCompleteListener(OnCompleteListener {
                _ -> Log.i("FireDB", "SUCCESS")
        })
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

    fun generateDALLEImage(prompt : String, callback: (Bitmap) -> Unit) {
        val url = "https://api.openai.com/v1/images/generations"

        val requestBody="""
            {
                "model": "dall-e-3",
                "prompt": "$prompt",
                "n": 1,
                "size": "1024x1024"
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
                Log.i("SUCCESS", "Api call success")
                val body = response.body?.string()
                Log.i("SUCCESS", "$body")

                val jsonObject = JSONObject(body)
                val jsonArray : JSONArray = jsonObject.getJSONArray("data")
                val message = jsonArray.getJSONObject(0).getString("url")

                val imageURL : URL = URL(message)

                bitmapOutput = BitmapFactory.decodeStream(imageURL.openStream())

                val bitmapImageScaled = Bitmap.createScaledBitmap(bitmapOutput, imageView.width, imageView.height, true)

                callback(bitmapImageScaled)
            }

        })

    }

    @SuppressLint("MissingPermission")
    private fun getLocation() {
        val location = fusedLocationProviderClient?.lastLocation
        if (location != null) {
            location.addOnSuccessListener {
                if(it!=null) {
                    latitude = it.latitude
                    longitude = it.longitude
                    Log.i("PhotoLocation", "long: $longitude, lat: $latitude")

                    type?.let { it1 -> addDataToDB(it1, longitude!!, latitude!!) }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        TODO("Not yet implemented")
    }


}
