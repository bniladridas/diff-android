package com.bniladridas.diff.data

import android.content.Context
import androidx.core.content.edit
import com.bniladridas.diff.model.AiDraft
import com.bniladridas.diff.model.RepoRef
import com.bniladridas.diff.model.SavedPull
import org.json.JSONArray
import org.json.JSONObject

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
    val userId: String,
    val accessToken: String,
    val refreshToken: String = "",
) {
    val isComplete: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()
    val canRefresh: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank() && refreshToken.isNotBlank()
}

class LocalPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("diff_local_state", Context.MODE_PRIVATE)
    private val secureTokenStore = SecureTokenStore()

    fun loadDefaultRepo(fallback: RepoRef): RepoRef {
        val owner = preferences.getString(DefaultOwnerKey, null)?.takeIf { it.isNotBlank() }
        val repo = preferences.getString(DefaultRepoKey, null)?.takeIf { it.isNotBlank() }
        return if (owner != null && repo != null) RepoRef(owner, repo) else fallback
    }

    fun saveDefaultRepo(repo: RepoRef) {
        preferences.edit {
            putString(DefaultOwnerKey, repo.owner)
            putString(DefaultRepoKey, repo.repo)
        }
    }

    fun loadRecentRepos(): List<RepoRef> {
        val raw = preferences.getString(RecentReposKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                RepoRef(
                    owner = item.optString("owner"),
                    repo = item.optString("repo"),
                )
            }.filter { it.owner.isNotBlank() && it.repo.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun saveRecentRepos(repos: List<RepoRef>) {
        preferences.edit {
            putString(RecentReposKey, repos.take(MaxRecentRepos).repoRefsToJson())
        }
    }

    fun loadSavedPulls(): List<SavedPull> {
        val raw = preferences.getString(SavedPullsKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SavedPull(
                    owner = item.optString("owner"),
                    repo = item.optString("repo"),
                    number = item.optInt("number", item.optInt("pull_number")),
                    title = item.optString("title"),
                    state = item.optString("state"),
                    htmlUrl = item.optString("html_url"),
                    draft = item.optBoolean("draft"),
                    savedAt = item.optLong("savedAt", parseIsoTimestamp(item.optString("saved_at"))),
                )
            }.filter { it.owner.isNotBlank() && it.repo.isNotBlank() && it.number > 0 }
        }.getOrDefault(emptyList())
    }

    fun saveSavedPulls(pulls: List<SavedPull>) {
        preferences.edit {
            putString(SavedPullsKey, pulls.take(MaxSavedPulls).savedPullsToJson())
        }
    }

    fun loadAiDrafts(): List<AiDraft> {
        val raw = preferences.getString(AiDraftsKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                item.toAiDraft()
            }.filter { it.id.isNotBlank() && it.owner.isNotBlank() && it.repo.isNotBlank() && it.pullNumber > 0 }
        }.getOrDefault(emptyList())
    }

    fun saveAiDrafts(drafts: List<AiDraft>) {
        preferences.edit {
            putString(AiDraftsKey, drafts.take(MaxAiDrafts).aiDraftsToJson())
        }
    }

    fun loadGitHubToken(): String {
        preferences.getString(EncryptedGitHubTokenKey, null)
            ?.let { encrypted ->
                secureTokenStore.decrypt(encrypted)?.let { return it }
            }

        val legacyToken = preferences.getString(GitHubTokenKey, null).orEmpty()
        if (legacyToken.isNotBlank()) {
            saveGitHubToken(legacyToken)
        }
        return legacyToken
    }

    fun isGitHubTokenFromSupabase(): Boolean =
        preferences.getBoolean(GitHubTokenFromSupabaseKey, false)

    fun saveGitHubToken(token: String, fromSupabase: Boolean = false) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            clearGitHubToken()
            return
        }
        val encrypted = secureTokenStore.encrypt(cleanToken)
        preferences.edit {
            remove(GitHubTokenKey)
            if (encrypted != null) {
                putString(EncryptedGitHubTokenKey, encrypted)
                putBoolean(GitHubTokenFromSupabaseKey, fromSupabase)
            } else {
                remove(EncryptedGitHubTokenKey)
                remove(GitHubTokenFromSupabaseKey)
            }
        }
    }

    fun clearGitHubToken() {
        preferences.edit {
            remove(GitHubTokenKey)
            remove(EncryptedGitHubTokenKey)
            remove(GitHubTokenFromSupabaseKey)
        }
    }

    fun loadGeminiApiKey(): String =
        preferences.getString(EncryptedGeminiApiKeyKey, null)
            ?.let { secureTokenStore.decrypt(it) }
            .orEmpty()

    fun saveGeminiApiKey(key: String) {
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) {
            clearGeminiApiKey()
            return
        }
        val encrypted = secureTokenStore.encrypt(cleanKey)
        preferences.edit {
            if (encrypted != null) {
                putString(EncryptedGeminiApiKeyKey, encrypted)
            } else {
                remove(EncryptedGeminiApiKeyKey)
            }
        }
    }

    fun clearGeminiApiKey() {
        preferences.edit {
            remove(EncryptedGeminiApiKeyKey)
        }
    }

    fun loadSupabaseConfig(): SupabaseConfig =
        SupabaseConfig(
            url = preferences.getString(SupabaseUrlKey, null).orEmpty(),
            anonKey = preferences.getString(EncryptedSupabaseAnonKeyKey, null)
                ?.let { secureTokenStore.decrypt(it) }
                .orEmpty(),
            userId = preferences.getString(SupabaseUserIdKey, null).orEmpty(),
            accessToken = preferences.getString(EncryptedSupabaseAccessTokenKey, null)
                ?.let { secureTokenStore.decrypt(it) }
                .orEmpty(),
            refreshToken = preferences.getString(EncryptedSupabaseRefreshTokenKey, null)
                ?.let { secureTokenStore.decrypt(it) }
                .orEmpty(),
        )

    fun loadSupabaseAuthVerifier(): String =
        preferences.getString(SupabaseAuthVerifierKey, null).orEmpty()

    fun saveSupabaseAuthVerifier(verifier: String) {
        preferences.edit {
            putString(SupabaseAuthVerifierKey, verifier)
        }
    }

    fun clearSupabaseAuthVerifier() {
        preferences.edit {
            remove(SupabaseAuthVerifierKey)
        }
    }

    fun saveSupabaseConfig(config: SupabaseConfig) {
        val cleanAnonKey = config.anonKey.trim()
        val cleanAccessToken = config.accessToken.trim()
        val cleanRefreshToken = config.refreshToken.trim()
        val encryptedAnonKey = cleanAnonKey.takeIf { it.isNotBlank() }?.let(secureTokenStore::encrypt)
        val encryptedAccessToken = cleanAccessToken.takeIf { it.isNotBlank() }?.let(secureTokenStore::encrypt)
        val encryptedRefreshToken = cleanRefreshToken.takeIf { it.isNotBlank() }?.let(secureTokenStore::encrypt)
        preferences.edit {
            putString(SupabaseUrlKey, config.url.trim().trimEnd('/'))
            putString(SupabaseUserIdKey, config.userId.trim())
            if (encryptedAnonKey != null) {
                putString(EncryptedSupabaseAnonKeyKey, encryptedAnonKey)
            } else {
                remove(EncryptedSupabaseAnonKeyKey)
            }
            if (encryptedAccessToken != null) {
                putString(EncryptedSupabaseAccessTokenKey, encryptedAccessToken)
            } else {
                remove(EncryptedSupabaseAccessTokenKey)
            }
            if (encryptedRefreshToken != null) {
                putString(EncryptedSupabaseRefreshTokenKey, encryptedRefreshToken)
            } else {
                remove(EncryptedSupabaseRefreshTokenKey)
            }
        }
    }

    fun clearSupabaseConfig() {
        preferences.edit {
            remove(SupabaseUrlKey)
            remove(SupabaseUserIdKey)
            remove(EncryptedSupabaseAnonKeyKey)
            remove(EncryptedSupabaseAccessTokenKey)
            remove(EncryptedSupabaseRefreshTokenKey)
            remove(SupabaseAuthVerifierKey)
        }
    }

    fun clearSavedAppState() {
        preferences.edit {
            remove(DefaultOwnerKey)
            remove(DefaultRepoKey)
            remove(RecentReposKey)
            remove(SavedPullsKey)
            remove(AiDraftsKey)
        }
    }

    private fun List<RepoRef>.repoRefsToJson(): String {
        val array = JSONArray()
        forEach { repo ->
            array.put(
                JSONObject()
                    .put("owner", repo.owner)
                    .put("repo", repo.repo),
            )
        }
        return array.toString()
    }

    private fun List<SavedPull>.savedPullsToJson(): String {
        val array = JSONArray()
        forEach { pull ->
            array.put(
                JSONObject()
                    .put("owner", pull.owner)
                    .put("repo", pull.repo)
                    .put("number", pull.number)
                    .put("pull_number", pull.number)
                    .put("title", pull.title)
                    .put("html_url", pull.htmlUrl)
                    .put("state", pull.state)
                    .put("draft", pull.draft)
                    .put("savedAt", pull.savedAt)
                    .put(
                        "saved_at",
                        java.time.Instant.ofEpochMilli(pull.savedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
                            .toString(),
                    ),
            )
        }
        return array.toString()
    }

    private fun parseIsoTimestamp(value: String): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }
            .getOrDefault(0L)

    private fun JSONObject.toAiDraft(): AiDraft =
        AiDraft(
            id = optString("id"),
            owner = optString("owner"),
            repo = optString("repo"),
            pullNumber = optInt("pull_number", optInt("pullNumber")),
            pullTitle = optString("pull_title", optString("pullTitle")),
            branch = optString("branch"),
            path = optString("path"),
            commentId = optLong("comment_id", optLong("commentId")),
            commentBody = optString("comment_body", optString("commentBody")),
            originalContent = optString("original_content", optString("originalContent")),
            draftContent = optString("draft_content", optString("draftContent")),
            summary = optString("summary"),
            updatedAt = optLong("updatedAt", parseIsoTimestamp(optString("updated_at"))),
        )

    private fun List<AiDraft>.aiDraftsToJson(): String {
        val array = JSONArray()
        forEach { draft ->
            array.put(draft.toJson())
        }
        return array.toString()
    }

    private fun AiDraft.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("owner", owner)
            .put("repo", repo)
            .put("pull_number", pullNumber)
            .put("pull_title", pullTitle)
            .put("branch", branch)
            .put("path", path)
            .put("comment_id", commentId)
            .put("comment_body", commentBody)
            .put("original_content", originalContent)
            .put("draft_content", draftContent)
            .put("summary", summary)
            .put("updatedAt", updatedAt)
            .put(
                "updated_at",
                java.time.Instant.ofEpochMilli(updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()).toString(),
            )

    private companion object {
        const val DefaultOwnerKey = "default_owner"
        const val DefaultRepoKey = "default_repo"
        const val RecentReposKey = "recent_repos"
        const val SavedPullsKey = "saved_pulls"
        const val AiDraftsKey = "ai_drafts"
        const val GitHubTokenKey = "github_token"
        const val EncryptedGitHubTokenKey = "github_token_encrypted"
        const val GitHubTokenFromSupabaseKey = "github_token_from_supabase"
        const val EncryptedGeminiApiKeyKey = "gemini_api_key_encrypted"
        const val SupabaseUrlKey = "supabase_url"
        const val SupabaseUserIdKey = "supabase_user_id"
        const val SupabaseAuthVerifierKey = "supabase_auth_verifier"
        const val EncryptedSupabaseAnonKeyKey = "supabase_anon_key_encrypted"
        const val EncryptedSupabaseAccessTokenKey = "supabase_access_token_encrypted"
        const val EncryptedSupabaseRefreshTokenKey = "supabase_refresh_token_encrypted"
        const val MaxRecentRepos = 6
        const val MaxSavedPulls = 20
        const val MaxAiDrafts = 20
    }
}
