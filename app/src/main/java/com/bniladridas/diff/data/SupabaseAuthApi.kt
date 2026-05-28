package com.bniladridas.diff.data

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class SupabaseAuthStart(
    val authUri: Uri,
    val verifier: String,
)

data class SupabaseAuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val providerToken: String,
)

object SupabaseAuthApi {
    fun createGitHubAuthUri(config: SupabaseConfig): SupabaseAuthStart {
        require(config.url.isNotBlank()) { "Add a Supabase URL before signing in." }
        require(config.anonKey.isNotBlank()) { "Add a Supabase anon key before signing in." }
        val verifier = createCodeVerifier()
        val challenge = codeChallenge(verifier)
        val uri = config.url.trim().trimEnd('/').toUri()
            .buildUpon()
            .appendEncodedPath("auth/v1/authorize")
            .appendQueryParameter("provider", "github")
            .appendQueryParameter("redirect_to", RedirectUri)
            .appendQueryParameter("scopes", "repo read:user user:email")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
        return SupabaseAuthStart(uri, verifier)
    }

    suspend fun exchangeCode(
        config: SupabaseConfig,
        code: String,
        verifier: String,
    ): Result<SupabaseAuthSession> =
        runCatching {
            require(config.url.isNotBlank()) { "Supabase URL is required." }
            require(config.anonKey.isNotBlank()) { "Supabase anon key is required." }
            require(code.isNotBlank()) { "Supabase callback did not include an auth code." }
            require(verifier.isNotBlank()) { "Supabase sign-in verifier is missing. Start sign-in again on this device." }
            requestToken(config, code, verifier)
        }

    suspend fun refreshSession(config: SupabaseConfig): Result<SupabaseAuthSession> =
        runCatching {
            require(config.url.isNotBlank()) { "Supabase URL is required." }
            require(config.anonKey.isNotBlank()) { "Supabase anon key is required." }
            require(config.refreshToken.isNotBlank()) { "Supabase refresh token is missing. Sign in again." }
            requestRefresh(config)
        }

    suspend fun signOut(config: SupabaseConfig): Result<Unit> =
        runCatching {
            require(config.url.isNotBlank()) { "Supabase URL is required." }
            require(config.anonKey.isNotBlank()) { "Supabase anon key is required." }
            require(config.accessToken.isNotBlank()) { "No active Supabase session to sign out." }
            requestSignOut(config)
        }

    private suspend fun requestToken(
        config: SupabaseConfig,
        code: String,
        verifier: String,
    ): SupabaseAuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("auth_code", code)
            .put("code_verifier", verifier)
            .toString()
        val connection = (URL("${config.url.trim().trimEnd('/')}/auth/v1/token?grant_type=pkce").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", config.anonKey.trim())
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DIFF-Android")
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                error("Supabase auth returned ${connection.responseCode}: ${extractMessage(responseBody) ?: responseBody.take(180)}")
            }
            JSONObject(responseBody).toSession()
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestRefresh(config: SupabaseConfig): SupabaseAuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("refresh_token", config.refreshToken.trim())
            .toString()
        val connection = (URL("${config.url.trim().trimEnd('/')}/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", config.anonKey.trim())
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DIFF-Android")
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                error("Supabase refresh returned ${connection.responseCode}: ${extractMessage(responseBody) ?: responseBody.take(180)}")
            }
            JSONObject(responseBody).toSession()
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestSignOut(config: SupabaseConfig): Unit = withContext(Dispatchers.IO) {
        val connection = (URL("${config.url.trim().trimEnd('/')}/auth/v1/logout").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", config.anonKey.trim())
            setRequestProperty("Authorization", "Bearer ${config.accessToken.trim()}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DIFF-Android")
        }

        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                error("Supabase sign-out returned ${connection.responseCode}: ${extractMessage(responseBody) ?: responseBody.take(180)}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toSession(): SupabaseAuthSession {
        val user = optJSONObject("user")
        return SupabaseAuthSession(
            userId = user?.optString("id").orEmpty(),
            accessToken = optString("access_token"),
            refreshToken = optString("refresh_token"),
            providerToken = optString("provider_token"),
        ).also {
            require(it.userId.isNotBlank()) { "Supabase auth did not return a user id." }
            require(it.accessToken.isNotBlank()) { "Supabase auth did not return an access token." }
        }
    }

    private fun createCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun extractMessage(body: String): String? =
        runCatching {
            val json = JSONObject(body)
            json.optString("msg").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()

    const val RedirectUri = "diff://auth/callback"
}
