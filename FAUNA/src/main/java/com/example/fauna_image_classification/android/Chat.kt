package com.example.fauna_image_classification.android

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.util.concurrent.TimeUnit


class Chat : AppCompatActivity() {
    private val client = OkHttpClient()
        .newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey : String = "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"

    private var recyclerView : RecyclerView? = null
    private var welcomeTextView : TextView? = null
    private var messageEditText : EditText? = null
    private var sendButton : ImageButton? = null

    lateinit var messageList : MutableList<Message>
    lateinit var messageAdapter : MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chat_popup)

        recyclerView = findViewById(R.id.recycler_view)
        welcomeTextView = findViewById(R.id.welcome_text)
        messageEditText = findViewById(R.id.message_edit_text)
        sendButton = findViewById(R.id.send_button)

        messageList = ArrayList<Message>()

        //imageView = findViewById<ImageView>(R.id.imageView) as ImageView

        // setup recycler view
        messageAdapter = MessageAdapter(messageList)
        recyclerView?.adapter = messageAdapter
        var llm : LinearLayoutManager = LinearLayoutManager(this)
        llm.stackFromEnd = true
        recyclerView?.layoutManager = llm

        // user input
        sendButton?.setOnClickListener( View.OnClickListener {
            var question : String = messageEditText?.text.toString().trim()
            //Toast.makeText(this, question, Toast.LENGTH_LONG).show()
            addToChat(question, Message.SENT_BY_ME)
            messageEditText?.setText("")
            callGPTAPI(question) // call openAI API for chatGPT
            welcomeTextView?.visibility = View.GONE
        })
    }

    fun addToChat(message : String, sentBy : String) {
        runOnUiThread {
            messageList.add(
                Message(
                    message,
                    sentBy
                )
            )
            messageAdapter.notifyDataSetChanged()
            recyclerView?.smoothScrollToPosition(messageAdapter.itemCount)
        }
    }

    fun addResponse(response : String) {
        //messageList.removeAt(messageList.size - 1)
        addToChat(response, Message.SENT_BY_BOT)
    }

    fun callGPTAPI(question: String) {
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
                addResponse("Failed to load response");
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

                addResponse(textResult.trim())
            }
        })
        //callback("textResult")

    }

}