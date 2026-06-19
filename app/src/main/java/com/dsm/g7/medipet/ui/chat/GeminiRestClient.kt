package com.dsm.g7.medipet.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object GeminiRestClient {

    private const val MODEL = "gemini-2.5-flash"
    private const val BASE  = "https://generativelanguage.googleapis.com/v1beta/models"

    /**
     * Sends a multi-turn conversation to the Gemini REST API (free tier, no Firebase billing).
     * [contents] is ordered list of (role, text) pairs — "user" / "model".
     * Returns the model's response text.
     */
    suspend fun chat(
        apiKey: String,
        contents: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {

        val contentsJson = JSONArray().apply {
            contents.forEach { (role, text) ->
                put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", text))
                    })
                })
            }
        }

        val body = JSONObject().apply {
            put("contents", contentsJson)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 1024)
            })
            put("safetySettings", JSONArray().apply {
                listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
                ).forEach { category ->
                    put(JSONObject().apply {
                        put("category", category)
                        put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)

        val url = URL("$BASE/$MODEL:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod    = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            doOutput         = true
            connectTimeout   = 30_000
            readTimeout      = 60_000
        }

        try {
            conn.outputStream.use { it.write(body) }

            val code   = conn.responseCode
            val stream = if (code == 200) conn.inputStream else conn.errorStream
            val raw    = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (code != 200) throw Exception("Gemini API $code: $raw")

            JSONObject(raw)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

        } finally {
            conn.disconnect()
        }
    }
}
