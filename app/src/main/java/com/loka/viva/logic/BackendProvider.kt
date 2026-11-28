package com.loka.viva.logic

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private const val ENDPOINT = "https://viva-ai-backend.gadthd.workers.dev/chat"

    fun sendMessage(messages: List<ChatMessage>, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val payload = ChatRequest(messages)
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onError(e.message ?: "Network error")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) { onError("HTTP ${response.code}"); return }
                val body2 = response.body?.string() ?: ""
                try {
                    val resp = gson.fromJson(body2, ChatResponse::class.java)
                    onSuccess(resp.response ?: "No response")
                } catch (e: Exception) {
                    onError("Parse error: ${e.message}")
                }
            }
        })
    }

    fun sendSimple(prompt: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val messages = listOf(ChatMessage("user", prompt))
        sendMessage(messages, onSuccess, onError)
    }

    data class ChatMessage(val role: String, val content: String)
    data class ChatRequest(val messages: List<ChatMessage>,
        val model: String = "@cf/meta/llama-3.1-8b-instruct",
        val maxTokens: Int = 512, val temperature: Float = 0.7f)
    data class ChatResponse(val response: String?, val error: String?)
}
