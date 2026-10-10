package com.odh.healthhub.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal client for the Claude Messages API. */
class ClaudeClient(private val apiKey: String, private val model: String) {

    class ApiException(message: String) : Exception(message)

    suspend fun complete(system: String, user: String, maxTokens: Int = 1800): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw ApiException("No Claude API key set. Add one in Settings.")
            val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                        .getOrDefault(text.take(300))
                    throw ApiException(
                        when (code) {
                            401 -> "Claude rejected the API key (401). Check it in Settings."
                            404 -> "Model not found: $model. Check the model name in Settings. ($msg)"
                            429 -> "Rate limited by the Claude API. Try again shortly."
                            else -> "Claude API error $code: $msg"
                        }
                    )
                }
                val content = JSONObject(text).getJSONArray("content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.getString("text"))
                    }
                }.trim()
            } finally {
                conn.disconnect()
            }
        }
}
