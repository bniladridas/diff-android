package com.bniladridas.diff.data

import com.bniladridas.diff.model.RepoRef
import com.bniladridas.diff.model.SavedPull
import com.bniladridas.diff.model.AiDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant

data class SyncedPreferences(
    val defaultRepo: RepoRef?,
    val recentRepos: List<RepoRef>,
    val savedPulls: List<SavedPull>,
    val aiDrafts: List<AiDraft>,
)

object SupabasePreferencesApi {
    internal fun isMissingAiDraftsColumnForTest(message: String): Boolean =
        isMissingAiDraftsColumnError(message)

    suspend fun fetchPreferences(config: SupabaseConfig): Result<SyncedPreferences> =
        runCatching {
            require(config.isComplete) { "Add Supabase URL, anon key, user id, and access token before syncing." }
            val userId = URLEncoder.encode(config.userId.trim(), Charsets.UTF_8.name())
            val select = "user_id,theme,default_repo_owner,default_repo_name,recent_repos,saved_pulls,ai_drafts,updated_at"
            val body = runCatching {
                request(
                    config = config,
                    method = "GET",
                    path = "/rest/v1/user_preferences?select=$select&user_id=eq.$userId&limit=1",
                )
            }.getOrElse { error ->
                if (!isMissingAiDraftsColumnError(error.message.orEmpty())) throw error
                val fallbackSelect = "user_id,theme,default_repo_owner,default_repo_name,recent_repos,saved_pulls,updated_at"
                request(
                    config = config,
                    method = "GET",
                    path = "/rest/v1/user_preferences?select=$fallbackSelect&user_id=eq.$userId&limit=1",
                )
            }
            val rows = JSONArray(body)
            rows.optJSONObject(0)?.toSyncedPreferences() ?: SyncedPreferences(
                defaultRepo = null,
                recentRepos = emptyList(),
                savedPulls = emptyList(),
                aiDrafts = emptyList(),
            )
        }

    suspend fun upsertPreferences(
        config: SupabaseConfig,
        defaultRepo: RepoRef,
        recentRepos: List<RepoRef>,
        savedPulls: List<SavedPull>,
        aiDrafts: List<AiDraft>,
    ): Result<Unit> =
        runCatching {
            require(config.isComplete) { "Add Supabase URL, anon key, user id, and access token before syncing." }
            val payload = JSONObject()
                .put("user_id", config.userId.trim())
                .put("theme", "graphite")
                .put("default_repo_owner", defaultRepo.owner)
                .put("default_repo_name", defaultRepo.repo)
                .put("recent_repos", recentRepos.toRecentReposJson())
                .put("saved_pulls", savedPulls.toSavedPullsJson())
                .put("ai_drafts", aiDrafts.toAiDraftsJson())
            runCatching {
                request(
                    config = config,
                    method = "POST",
                    path = "/rest/v1/user_preferences?on_conflict=user_id",
                    body = payload.toString(),
                    prefer = "resolution=merge-duplicates,return=minimal",
                )
            }.getOrElse { error ->
                if (!isMissingAiDraftsColumnError(error.message.orEmpty())) throw error
                payload.remove("ai_drafts")
                request(
                    config = config,
                    method = "POST",
                    path = "/rest/v1/user_preferences?on_conflict=user_id",
                    body = payload.toString(),
                    prefer = "resolution=merge-duplicates,return=minimal",
                )
            }
        }

    private suspend fun request(
        config: SupabaseConfig,
        method: String,
        path: String,
        body: String? = null,
        prefer: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(config.url.trim().trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", config.anonKey.trim())
            setRequestProperty("Authorization", "Bearer ${config.accessToken.trim()}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "DIFF-Android")
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                error("Supabase returned ${connection.responseCode}: ${extractMessage(responseBody) ?: responseBody.take(180)}")
            }
            responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toSyncedPreferences(): SyncedPreferences {
        val owner = optString("default_repo_owner").takeIf { it.isNotBlank() }
        val repo = optString("default_repo_name").takeIf { it.isNotBlank() }
        return SyncedPreferences(
            defaultRepo = if (owner != null && repo != null) RepoRef(owner, repo) else null,
            recentRepos = optJSONArray("recent_repos").toRecentRepos(),
            savedPulls = optJSONArray("saved_pulls").toSavedPulls(),
            aiDrafts = optJSONArray("ai_drafts").toAiDrafts(),
        )
    }

    private fun JSONArray?.toRecentRepos(): List<RepoRef> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            val item = optJSONObject(index) ?: JSONObject()
            RepoRef(item.optString("owner"), item.optString("repo"))
        }.filter { it.owner.isNotBlank() && it.repo.isNotBlank() }
    }

    private fun JSONArray?.toSavedPulls(): List<SavedPull> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            val item = optJSONObject(index) ?: JSONObject()
            SavedPull(
                owner = item.optString("owner"),
                repo = item.optString("repo"),
                number = item.optInt("pull_number", item.optInt("number")),
                title = item.optString("title"),
                htmlUrl = item.optString("html_url"),
                state = item.optString("state"),
                draft = item.optBoolean("draft"),
                savedAt = parseIsoTimestamp(item.optString("saved_at")),
            )
        }.filter { it.owner.isNotBlank() && it.repo.isNotBlank() && it.number > 0 }
    }

    private fun List<RepoRef>.toRecentReposJson(): JSONArray {
        val now = Instant.now().toString()
        val array = JSONArray()
        take(MaxRecentRepos).forEach { repo ->
            array.put(
                JSONObject()
                    .put("owner", repo.owner)
                    .put("repo", repo.repo)
                    .put("last_viewed_at", now),
            )
        }
        return array
    }

    private fun List<SavedPull>.toSavedPullsJson(): JSONArray {
        val array = JSONArray()
        take(MaxSavedPulls).forEach { pull ->
            array.put(
                JSONObject()
                    .put("owner", pull.owner)
                    .put("repo", pull.repo)
                    .put("pull_number", pull.number)
                    .put("title", pull.title)
                    .put("html_url", pull.htmlUrl.ifBlank { "https://github.com/${pull.owner}/${pull.repo}/pull/${pull.number}" })
                    .put("state", pull.state)
                    .put("draft", pull.draft)
                    .put("saved_at", Instant.ofEpochMilli(pull.savedAt.takeIf { it > 0L } ?: System.currentTimeMillis()).toString()),
            )
        }
        return array
    }

    private fun JSONArray?.toAiDrafts(): List<AiDraft> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            val item = optJSONObject(index) ?: JSONObject()
            AiDraft(
                id = item.optString("id"),
                owner = item.optString("owner"),
                repo = item.optString("repo"),
                pullNumber = item.optInt("pull_number"),
                pullTitle = item.optString("pull_title"),
                branch = item.optString("branch"),
                path = item.optString("path"),
                commentId = item.optLong("comment_id"),
                commentBody = item.optString("comment_body"),
                originalContent = item.optString("original_content"),
                draftContent = item.optString("draft_content"),
                summary = item.optString("summary"),
                updatedAt = parseIsoTimestamp(item.optString("updated_at")),
            )
        }.filter { it.id.isNotBlank() && it.owner.isNotBlank() && it.repo.isNotBlank() && it.pullNumber > 0 }
    }

    private fun List<AiDraft>.toAiDraftsJson(): JSONArray {
        val array = JSONArray()
        take(MaxAiDrafts).forEach { draft ->
            array.put(
                JSONObject()
                    .put("id", draft.id)
                    .put("owner", draft.owner)
                    .put("repo", draft.repo)
                    .put("pull_number", draft.pullNumber)
                    .put("pull_title", draft.pullTitle)
                    .put("branch", draft.branch)
                    .put("path", draft.path)
                    .put("comment_id", draft.commentId)
                    .put("comment_body", draft.commentBody)
                    .put("original_content", draft.originalContent)
                    .put("draft_content", draft.draftContent)
                    .put("summary", draft.summary)
                    .put("updated_at", Instant.ofEpochMilli(draft.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()).toString()),
            )
        }
        return array
    }

    private fun parseIsoTimestamp(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())

    private fun extractMessage(body: String): String? =
        runCatching {
            val json = JSONObject(body)
            val message = json.optString("message").takeIf { it.isNotBlank() }
            val code = json.optString("code").takeIf { it.isNotBlank() }
            val details = json.optString("details").takeIf { it.isNotBlank() }
            listOfNotNull(code, message, details)
                .joinToString(": ")
                .takeIf { it.isNotBlank() }
                ?: json.optString("hint").takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun isMissingAiDraftsColumnError(message: String): Boolean {
        val normalized = message.lowercase()
        return "ai_drafts" in normalized &&
            (
                "pgrst204" in normalized ||
                    "42703" in normalized ||
                    "column" in normalized ||
                    "schema cache" in normalized ||
                    "does not exist" in normalized
                )
    }

    private const val MaxRecentRepos = 6
    private const val MaxSavedPulls = 20
    private const val MaxAiDrafts = 20
}
