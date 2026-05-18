package com.bniladridas.diff.data

import java.time.Instant
import java.time.ZoneId

object GitHubErrors {
    fun format(code: Int, body: String, resetEpochSeconds: Long? = null): String {
        val message = extractMessage(body) ?: body.take(160).ifBlank { "Request failed." }
        val resetAt = resetEpochSeconds
            ?.let { epochSeconds ->
                Instant.ofEpochSecond(epochSeconds)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .withNano(0)
                    .toString()
            }
        return when {
            code == 401 -> "GitHub authentication failed. Check the token in Account."
            code == 403 && message.contains("rate limit", ignoreCase = true) ->
                buildString {
                    append("GitHub rate limit exceeded.")
                    resetAt?.let { append(" Try again after $it.") }
                    append(" Add a token in Account for a higher limit.")
                }
            code == 404 -> "GitHub resource not found or token lacks access."
            code == 409 -> "GitHub reported a conflict. Refresh and check branch state."
            code == 422 -> "GitHub rejected the request: $message"
            else -> "GitHub returned $code: $message"
        }
    }

    private fun extractMessage(body: String): String? {
        val match = """"message"\s*:\s*"([^"]*)"""".toRegex().find(body) ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.takeIf { it.isNotBlank() && it != "null" }
    }
}
