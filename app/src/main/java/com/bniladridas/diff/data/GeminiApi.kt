package com.bniladridas.diff.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiApi {
    suspend fun generateDraftFix(apiKey: String, prompt: String): Result<String> =
        runCatching {
            if (apiKey.isBlank()) error("Add a Gemini API key in Account before generating fixes.")
            if (prompt.isBlank()) error("Draft Fix context is empty.")
            requestGenerateContent(apiKey.trim(), buildPrompt(prompt))
        }

    private suspend fun requestGenerateContent(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt)),
                        ),
                ),
            )
            .toString()

        val connection = (URL(Endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "DIFF-Android")
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                error("Gemini returned ${connection.responseCode}: ${extractMessage(body) ?: body.take(160)}")
            }
            val candidates = JSONObject(body).optJSONArray("candidates") ?: JSONArray()
            val content = candidates.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()
            parts.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                ?: error("Gemini did not return a draft fix.")
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(context: String): String =
        """
        You are helping implement a code review fix in the DIFF Android app workflow.
        Use the review comment and diff context below to draft the smallest safe code change.
        Return concise implementation guidance and, when possible, a unified diff-style patch.
        Do not invent files or APIs that are not implied by the context.

        $context
        """.trimIndent()

    private fun extractMessage(body: String): String? =
        """"message"\s*:\s*"([^"]*)"""".toRegex()
            .find(body)
            ?.groupValues
            ?.getOrNull(1)

    private const val Endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
}
