package com.bniladridas.diff.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bniladridas.diff.data.GeminiApi
import com.bniladridas.diff.data.GitHubApi
import com.bniladridas.diff.data.LocalPreferences
import com.bniladridas.diff.data.SupabaseAuthApi
import com.bniladridas.diff.data.SupabaseConfig
import com.bniladridas.diff.data.SupabasePreferencesApi
import com.bniladridas.diff.model.AiDraft
import com.bniladridas.diff.model.Branch
import com.bniladridas.diff.model.CheckRun
import com.bniladridas.diff.model.LoadState
import com.bniladridas.diff.model.CommentKind
import com.bniladridas.diff.model.PullDetails
import com.bniladridas.diff.model.PullFilter
import com.bniladridas.diff.model.PullComment
import com.bniladridas.diff.model.PullCommit
import com.bniladridas.diff.model.PullRequest
import com.bniladridas.diff.model.PullReview
import com.bniladridas.diff.model.RepoRef
import com.bniladridas.diff.model.RepoFileContent
import com.bniladridas.diff.model.RepoTreeItem
import com.bniladridas.diff.model.SavedPull
import com.bniladridas.diff.model.StreamMode
import com.bniladridas.diff.model.TimelineEvent
import com.bniladridas.diff.model.WorkspaceTab
import com.bniladridas.diff.ui.components.BrandMark
import com.bniladridas.diff.ui.components.BranchCard
import com.bniladridas.diff.ui.components.CheckCard
import com.bniladridas.diff.ui.components.CodeBuffer
import com.bniladridas.diff.ui.components.CommentCard
import com.bniladridas.diff.ui.components.CommitCard
import com.bniladridas.diff.ui.components.EmptyPanel
import com.bniladridas.diff.ui.components.ErrorPanel
import com.bniladridas.diff.ui.components.FileCard
import com.bniladridas.diff.ui.components.FileManifestItem
import com.bniladridas.diff.ui.components.LoadingRow
import com.bniladridas.diff.ui.components.PanelCard
import com.bniladridas.diff.ui.components.PullCard
import com.bniladridas.diff.ui.components.RepoFileCard
import com.bniladridas.diff.ui.components.ReviewCard
import com.bniladridas.diff.ui.components.SectionTitle
import com.bniladridas.diff.ui.components.Tag
import com.bniladridas.diff.ui.components.TimelineEventCard
import com.bniladridas.diff.ui.components.WorkspaceHeader
import com.bniladridas.diff.ui.components.WorkspaceTabs
import com.bniladridas.diff.ui.theme.BrandOrange
import com.bniladridas.diff.ui.theme.BrandOrangeSoft
import com.bniladridas.diff.ui.theme.DiffLine
import com.bniladridas.diff.ui.theme.DiffGreen
import com.bniladridas.diff.ui.theme.DiffGreenSoft
import com.bniladridas.diff.ui.theme.DiffRed
import com.bniladridas.diff.ui.theme.DiffRedSoft
import com.bniladridas.diff.ui.theme.PanelRaised
import com.bniladridas.diff.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SystemOwner = "harpertoken"
private const val SystemRepo = "harper"

private enum class MobilePane {
    Pulls,
    Workspace,
}

private sealed class HistoryEntry(open val date: String, open val key: String) {
    data class Commit(val commit: PullCommit) : HistoryEntry(commit.date, "commit-${commit.sha}")
    data class Comment(val comment: PullComment) : HistoryEntry(comment.createdAt, "comment-${comment.id}-${comment.createdAt}")
    data class Review(val review: PullReview) : HistoryEntry(review.submittedAt, "review-${review.author}-${review.submittedAt}")
    data class Timeline(val event: TimelineEvent) : HistoryEntry(event.date, "timeline-${event.id}")
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun DiffApp(
    authCallbackUri: Uri? = null,
    onAuthCallbackConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val contentListState = rememberLazyListState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val localPreferences = remember(context) { LocalPreferences(context) }
    val fallbackRepo = remember { RepoRef(SystemOwner, SystemRepo) }
    val initialRepo = remember { localPreferences.loadDefaultRepo(fallbackRepo) }
    var owner by remember { mutableStateOf(initialRepo.owner) }
    var repo by remember { mutableStateOf(initialRepo.repo) }
    var defaultRepo by remember { mutableStateOf(initialRepo) }
    var recentRepos by remember { mutableStateOf(localPreferences.loadRecentRepos()) }
    var savedPulls by remember { mutableStateOf(localPreferences.loadSavedPulls()) }
    var aiDrafts by remember { mutableStateOf(localPreferences.loadAiDrafts()) }
    var githubToken by remember { mutableStateOf(localPreferences.loadGitHubToken()) }
    var githubTokenFromSupabase by remember { mutableStateOf(localPreferences.isGitHubTokenFromSupabase()) }
    var geminiApiKey by remember { mutableStateOf(localPreferences.loadGeminiApiKey()) }
    var supabaseConfig by remember { mutableStateOf(localPreferences.loadSupabaseConfig()) }
    var githubLogin by remember { mutableStateOf<String?>(null) }
    var accountStatus by remember { mutableStateOf<String?>(null) }
    var preferencesSyncState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var discussionDraft by remember { mutableStateOf("") }
    var discussionWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var reviewDraft by remember { mutableStateOf("") }
    var reviewWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var inlineDraft by remember { mutableStateOf("") }
    var inlineLine by remember { mutableStateOf("") }
    var inlineFileName by remember { mutableStateOf<String?>(null) }
    var inlineWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var draftFixText by remember { mutableStateOf("") }
    var generatedDraftFix by remember { mutableStateOf("") }
    var activeAiDraftId by remember { mutableStateOf<String?>(null) }
    var draftFixState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var managementTitle by remember { mutableStateOf("") }
    var managementBody by remember { mutableStateOf("") }
    var managementLabels by remember { mutableStateOf("") }
    var managementWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var managementExpanded by remember { mutableStateOf(false) }
    var mergeMethod by remember { mutableStateOf("squash") }
    var mergeTitle by remember { mutableStateOf("") }
    var mergeMessage by remember { mutableStateOf("") }
    var mergeWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var updateBranchState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var deleteBranchState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var filter by remember { mutableStateOf(PullFilter.Open) }
    var streamMode by remember { mutableStateOf(StreamMode.Pulls) }
    var activeTab by remember { mutableStateOf(WorkspaceTab.Diff) }
    var pulls by remember { mutableStateOf<List<PullRequest>>(emptyList()) }
    var branches by remember { mutableStateOf<List<Branch>>(emptyList()) }
    var defaultBranch by remember { mutableStateOf("main") }
    var repoFiles by remember { mutableStateOf<List<RepoTreeItem>>(emptyList()) }
    var selectedBranch by remember { mutableStateOf<Branch?>(null) }
    var selectedRepoFilePath by remember { mutableStateOf<String?>(null) }
    var repoFileContent by remember { mutableStateOf<RepoFileContent?>(null) }
    var selectedPull by remember { mutableStateOf<PullRequest?>(null) }
    var details by remember { mutableStateOf<PullDetails?>(null) }
    var branchDetails by remember { mutableStateOf<PullDetails?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var highlightedDiffPath by remember { mutableStateOf<String?>(null) }
    var highlightedDiffLine by remember { mutableStateOf<Int?>(null) }
    var highlightedDiffStartLine by remember { mutableStateOf<Int?>(null) }
    var highlightedDiffSide by remember { mutableStateOf<String?>(null) }
    var highlightedDiffStartSide by remember { mutableStateOf<String?>(null) }
    var pane by remember { mutableStateOf(MobilePane.Pulls) }
    var lastRootBackAt by remember { mutableLongStateOf(0L) }
    var listState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var branchesState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var repoTreeState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var repoFileState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var detailState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var branchDetailState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var checksState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var checksPolling by remember { mutableStateOf(false) }
    var checkLogs by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var checkLogStates by remember { mutableStateOf<Map<Long, LoadState>>(emptyMap()) }
    var repoEditContent by remember { mutableStateOf("") }
    var repoCommitMessage by remember { mutableStateOf("") }
    var conflictResolutionHint by remember { mutableStateOf<String?>(null) }
    var conflictBaseContent by remember { mutableStateOf<RepoFileContent?>(null) }
    var conflictBaseState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var conflictBaseRef by remember { mutableStateOf<String?>(null) }
    var repoWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var repoCreateMode by remember { mutableStateOf(false) }
    var repoNewFilePath by remember { mutableStateOf("") }
    var repoNewFileContent by remember { mutableStateOf("") }
    var repoCreateState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var repoNewBranchName by remember { mutableStateOf("") }
    var branchWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var pullTitle by remember { mutableStateOf("") }
    var pullBody by remember { mutableStateOf("") }
    var pullBaseBranch by remember { mutableStateOf("main") }
    var pullWriteState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
    var listRequestId by remember { mutableIntStateOf(0) }
    var branchesRequestId by remember { mutableIntStateOf(0) }
    var repoTreeRequestId by remember { mutableIntStateOf(0) }
    var repoFileRequestId by remember { mutableIntStateOf(0) }
    var detailRequestId by remember { mutableIntStateOf(0) }
    var loadedRepoKey by remember { mutableStateOf("") }
    var showAccount by remember { mutableStateOf(false) }
    var pendingSavedPullNumber by remember { mutableStateOf<Int?>(null) }
    var repoSwitchRequest by remember { mutableIntStateOf(0) }

    suspend fun refreshSupabaseConfigIfNeeded(config: SupabaseConfig): SupabaseConfig {
        if (!config.canRefresh) return config
        return SupabaseAuthApi.refreshSession(config)
            .map { session ->
                config.copy(
                    userId = session.userId.ifBlank { config.userId },
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken.ifBlank { config.refreshToken },
                ).also { refreshed ->
                    supabaseConfig = refreshed
                    localPreferences.saveSupabaseConfig(refreshed)
                    if (session.providerToken.isNotBlank()) {
                        githubToken = session.providerToken
                        githubTokenFromSupabase = true
                        localPreferences.saveGitHubToken(session.providerToken, fromSupabase = true)
                        GitHubApi.setAuthToken(session.providerToken)
                    }
                }
            }
            .getOrElse { throw it }
    }

    fun pushSupabasePreferences(config: SupabaseConfig = supabaseConfig) {
        if (!config.isComplete) return
        scope.launch {
            preferencesSyncState = LoadState.Loading
            val syncConfig = runCatching { refreshSupabaseConfigIfNeeded(config) }
                .getOrElse {
                    preferencesSyncState = LoadState.Failed(it.message ?: "Supabase session refresh failed.")
                    accountStatus = it.message ?: "Supabase session refresh failed."
                    return@launch
                }
            SupabasePreferencesApi.upsertPreferences(
                config = syncConfig,
                defaultRepo = defaultRepo,
                recentRepos = recentRepos,
                savedPulls = savedPulls,
                aiDrafts = aiDrafts,
            )
                .onSuccess {
                    preferencesSyncState = LoadState.Idle
                    accountStatus = "Preferences synced"
                }
                .onFailure {
                    preferencesSyncState = LoadState.Failed(it.message ?: "Preference sync failed.")
                    accountStatus = it.message ?: "Preference sync failed."
                }
        }
    }

    fun rememberRepo(nextOwner: String, nextRepo: String) {
        val repoRef = RepoRef(nextOwner, nextRepo)
        val nextRecent = (listOf(repoRef) + recentRepos)
            .distinctBy { "${it.owner}/${it.repo}" }
            .take(6)
        recentRepos = nextRecent
        localPreferences.saveRecentRepos(nextRecent)
        pushSupabasePreferences()
    }

    fun selectRepo(repoRef: RepoRef) {
        owner = repoRef.owner
        repo = repoRef.repo
        streamMode = StreamMode.Pulls
        filter = PullFilter.Open
        pane = MobilePane.Pulls
        repoSwitchRequest += 1
    }

    fun setDefaultRepo(repoRef: RepoRef) {
        defaultRepo = repoRef
        localPreferences.saveDefaultRepo(repoRef)
        pushSupabasePreferences()
    }

    fun toggleSavedPull(pull: PullRequest) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        val existingKey = "$safeOwner/$safeRepo#${pull.number}"
        val isSaved = savedPulls.any { "${it.owner}/${it.repo}#${it.number}" == existingKey }
        val nextSavedPulls = if (isSaved) {
            savedPulls.filterNot { "${it.owner}/${it.repo}#${it.number}" == existingKey }
        } else {
            (
                listOf(
                    SavedPull(
                        owner = safeOwner,
                        repo = safeRepo,
                        number = pull.number,
                        title = pull.title,
                        state = pull.state,
                        htmlUrl = pull.htmlUrl,
                        draft = pull.draft,
                        savedAt = System.currentTimeMillis(),
                    ),
                ) + savedPulls
            ).distinctBy { "${it.owner}/${it.repo}#${it.number}" }.take(20)
        }
        savedPulls = nextSavedPulls
        localPreferences.saveSavedPulls(nextSavedPulls)
        pushSupabasePreferences()
    }

    fun openSavedPull(savedPull: SavedPull) {
        owner = savedPull.owner
        repo = savedPull.repo
        streamMode = StreamMode.Pulls
        filter = PullFilter.All
        pendingSavedPullNumber = savedPull.number
        pane = MobilePane.Pulls
        repoSwitchRequest += 1
    }

    fun saveGitHubToken(token: String) {
        githubToken = token.trim()
        githubTokenFromSupabase = false
        localPreferences.saveGitHubToken(githubToken, fromSupabase = false)
        GitHubApi.setAuthToken(githubToken)
        accountStatus = if (githubToken.isBlank()) "Token cleared" else "Token saved"
    }

    fun clearGitHubToken() {
        githubToken = ""
        githubLogin = null
        githubTokenFromSupabase = false
        localPreferences.clearGitHubToken()
        GitHubApi.setAuthToken("")
        accountStatus = "Token cleared"
    }

    fun saveGeminiApiKey(key: String) {
        geminiApiKey = key.trim()
        localPreferences.saveGeminiApiKey(geminiApiKey)
        accountStatus = if (geminiApiKey.isBlank()) "Gemini key cleared" else "Gemini key saved"
    }

    fun clearGeminiApiKey() {
        geminiApiKey = ""
        localPreferences.clearGeminiApiKey()
        accountStatus = "Gemini key cleared"
    }

    fun saveSupabaseConfig(config: SupabaseConfig) {
        val cleanConfig = config.copy(
            url = config.url.trim().trimEnd('/'),
            anonKey = config.anonKey.trim(),
            userId = config.userId.trim(),
            accessToken = config.accessToken.trim(),
            refreshToken = config.refreshToken.trim(),
        )
        supabaseConfig = cleanConfig
        localPreferences.saveSupabaseConfig(cleanConfig)
        accountStatus = if (cleanConfig.isComplete) "Supabase sync configured" else "Supabase sync saved incomplete"
    }

    fun clearSupabaseConfig() {
        supabaseConfig = SupabaseConfig("", "", "", "", "")
        localPreferences.clearSupabaseConfig()
        preferencesSyncState = LoadState.Idle
        accountStatus = "Supabase sync cleared"
    }

    fun signOutSupabase(config: SupabaseConfig = supabaseConfig) {
        scope.launch {
            accountStatus = "Signing out"
            val cleanConfig = config.copy(
                url = config.url.trim().trimEnd('/'),
                anonKey = config.anonKey.trim(),
                userId = config.userId.trim(),
                accessToken = config.accessToken.trim(),
                refreshToken = config.refreshToken.trim(),
            )
            if (cleanConfig.accessToken.isNotBlank()) {
                SupabaseAuthApi.signOut(cleanConfig)
                    .onFailure {
                        accountStatus = it.message ?: "Supabase sign-out failed locally."
                    }
            }
            supabaseConfig = cleanConfig.copy(userId = "", accessToken = "", refreshToken = "")
            localPreferences.saveSupabaseConfig(supabaseConfig)
            if (githubTokenFromSupabase) {
                githubToken = ""
                githubLogin = null
                githubTokenFromSupabase = false
                localPreferences.clearGitHubToken()
                GitHubApi.setAuthToken("")
            }
            preferencesSyncState = LoadState.Idle
            accountStatus = "Signed out"
        }
    }

    fun startSupabaseGitHubSignIn(config: SupabaseConfig) {
        runCatching {
            val cleanConfig = config.copy(
                url = config.url.trim().trimEnd('/'),
                anonKey = config.anonKey.trim(),
                userId = config.userId.trim(),
                accessToken = config.accessToken.trim(),
                refreshToken = config.refreshToken.trim(),
            )
            saveSupabaseConfig(cleanConfig)
            SupabaseAuthApi.createGitHubAuthUri(cleanConfig)
        }
            .onSuccess { start ->
                localPreferences.saveSupabaseAuthVerifier(start.verifier)
                accountStatus = "Opening GitHub sign-in"
                context.startActivity(Intent(Intent.ACTION_VIEW, start.authUri))
            }
            .onFailure {
                accountStatus = it.message ?: "Could not start Supabase sign-in."
            }
    }

    fun pullSupabasePreferences(config: SupabaseConfig = supabaseConfig) {
        scope.launch {
            preferencesSyncState = LoadState.Loading
            val syncConfig = runCatching { refreshSupabaseConfigIfNeeded(config) }
                .getOrElse {
                    preferencesSyncState = LoadState.Failed(it.message ?: "Supabase session refresh failed.")
                    accountStatus = it.message ?: "Supabase session refresh failed."
                    return@launch
                }
            SupabasePreferencesApi.fetchPreferences(syncConfig)
                .onSuccess { synced ->
                    synced.defaultRepo?.let {
                        defaultRepo = it
                        owner = it.owner
                        repo = it.repo
                        localPreferences.saveDefaultRepo(it)
                    }
                    recentRepos = synced.recentRepos
                    savedPulls = synced.savedPulls
                    aiDrafts = synced.aiDrafts
                    localPreferences.saveRecentRepos(synced.recentRepos)
                    localPreferences.saveSavedPulls(synced.savedPulls)
                    localPreferences.saveAiDrafts(synced.aiDrafts)
                    preferencesSyncState = LoadState.Idle
                    accountStatus = "Preferences loaded from Supabase"
                }
                .onFailure {
                    preferencesSyncState = LoadState.Failed(it.message ?: "Could not load Supabase preferences.")
                    accountStatus = it.message ?: "Could not load Supabase preferences."
                }
        }
    }

    fun finishSupabaseGitHubSignIn(uri: Uri) {
        val error = uri.getQueryParameter("error_description")
            ?: uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            accountStatus = error
            localPreferences.clearSupabaseAuthVerifier()
            onAuthCallbackConsumed()
            return
        }
        val code = uri.getQueryParameter("code").orEmpty()
        val verifier = localPreferences.loadSupabaseAuthVerifier()
        scope.launch {
            accountStatus = "Completing GitHub sign-in"
            SupabaseAuthApi.exchangeCode(supabaseConfig, code, verifier)
                .onSuccess { session ->
                    localPreferences.clearSupabaseAuthVerifier()
                    val nextConfig = supabaseConfig.copy(
                        userId = session.userId,
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                    )
                    supabaseConfig = nextConfig
                    localPreferences.saveSupabaseConfig(nextConfig)
                    if (session.providerToken.isNotBlank()) {
                        githubToken = session.providerToken
                        githubTokenFromSupabase = true
                        localPreferences.saveGitHubToken(session.providerToken, fromSupabase = true)
                        GitHubApi.setAuthToken(session.providerToken)
                        GitHubApi.fetchViewer()
                            .onSuccess { githubLogin = it }
                    }
                    accountStatus = "Supabase sign-in complete"
                    pullSupabasePreferences(nextConfig)
                }
                .onFailure {
                    accountStatus = it.message ?: "Could not complete Supabase sign-in."
                }
            onAuthCallbackConsumed()
        }
    }

    fun clearSavedAppState() {
        localPreferences.clearSavedAppState()
        defaultRepo = fallbackRepo
        recentRepos = emptyList()
        savedPulls = emptyList()
        aiDrafts = emptyList()
        owner = fallbackRepo.owner
        repo = fallbackRepo.repo
        pulls = emptyList()
        branches = emptyList()
        repoFiles = emptyList()
        selectedPull = null
        selectedBranch = null
        selectedRepoFilePath = null
        repoFileContent = null
        conflictResolutionHint = null
        conflictBaseContent = null
        conflictBaseState = LoadState.Idle
        conflictBaseRef = null
        draftFixText = ""
        generatedDraftFix = ""
        activeAiDraftId = null
        details = null
        selectedFileName = null
        pane = MobilePane.Pulls
        streamMode = StreamMode.Pulls
        filter = PullFilter.Open
        listState = LoadState.Idle
        branchesState = LoadState.Idle
        repoTreeState = LoadState.Idle
        repoFileState = LoadState.Idle
        detailState = LoadState.Idle
        checksState = LoadState.Idle
        accountStatus = "Saved app state cleared"
    }

    fun saveAiDraft(draft: AiDraft) {
        val nextDrafts = (listOf(draft) + aiDrafts)
            .distinctBy { it.id }
            .take(20)
        aiDrafts = nextDrafts
        activeAiDraftId = draft.id
        localPreferences.saveAiDrafts(nextDrafts)
        pushSupabasePreferences()
    }

    fun deleteActiveAiDraft() {
        val draftId = activeAiDraftId ?: return
        val nextDrafts = aiDrafts.filterNot { it.id == draftId }
        aiDrafts = nextDrafts
        activeAiDraftId = null
        localPreferences.saveAiDrafts(nextDrafts)
        pushSupabasePreferences()
        accountStatus = "Draft Fix deleted"
    }

    fun openAiDraft(draft: AiDraft) {
        owner = draft.owner
        repo = draft.repo
        pendingSavedPullNumber = draft.pullNumber
        draftFixText = draft.originalContent
        generatedDraftFix = draft.draftContent
        reviewDraft = draft.summary.ifBlank { "Addressed review feedback on ${draft.path}." }
        inlineFileName = draft.path
        activeAiDraftId = draft.id
        activeTab = WorkspaceTab.Discussion
        pane = MobilePane.Pulls
        streamMode = StreamMode.Pulls
        filter = PullFilter.All
        repoSwitchRequest += 1
    }

    fun verifyGitHubToken(tokenOverride: String = githubToken) {
        scope.launch {
            accountStatus = "Checking token"
            GitHubApi.setAuthToken(tokenOverride)
            GitHubApi.fetchViewer()
                .onSuccess {
                    githubLogin = it
                    accountStatus = "Connected as $it"
                }
                .onFailure {
                    githubLogin = null
                    accountStatus = it.message ?: "Token check failed"
                }
        }
    }

    fun postDiscussionComment(pull: PullRequest) {
        val body = discussionDraft.trim()
        if (body.isBlank()) {
            discussionWriteState = LoadState.Failed("Add a comment before posting.")
            return
        }
        scope.launch {
            discussionWriteState = LoadState.Loading
            GitHubApi.postIssueComment(owner.trim(), repo.trim(), pull.number, body)
                .onSuccess { comment ->
                    details = details?.copy(
                        comments = (listOf(comment) + (details?.comments ?: emptyList())).sortedByDescending { it.createdAt },
                    )
                    discussionDraft = ""
                    discussionWriteState = LoadState.Idle
                }
                .onFailure {
                    discussionWriteState = LoadState.Failed(it.message ?: "Failed to post discussion comment.")
                }
        }
    }

    fun submitPullReview(pull: PullRequest, event: String) {
        val body = reviewDraft.trim()
        scope.launch {
            reviewWriteState = LoadState.Loading
            GitHubApi.submitReview(owner.trim(), repo.trim(), pull.number, event, body)
                .onSuccess { review ->
                    details = details?.copy(
                        reviews = (listOf(review) + (details?.reviews ?: emptyList())).sortedByDescending { it.submittedAt },
                    )
                    reviewDraft = ""
                    reviewWriteState = LoadState.Idle
                    activeTab = WorkspaceTab.History
                }
                .onFailure {
                    reviewWriteState = LoadState.Failed(it.message ?: "Failed to submit review.")
                }
        }
    }

    fun postInlineComment(pull: PullRequest) {
        val currentDetails = details
        val targetFile = inlineFileName
            ?: selectedFileName
            ?: currentDetails?.files?.firstOrNull()?.filename
        val line = inlineLine.trim().toIntOrNull() ?: 0
        scope.launch {
            inlineWriteState = LoadState.Loading
            GitHubApi.postInlineReviewComment(
                owner = owner.trim(),
                repo = repo.trim(),
                pull = pull,
                path = targetFile.orEmpty(),
                line = line,
                body = inlineDraft,
            )
                .onSuccess { comment ->
                    details = details?.copy(
                        comments = (listOf(comment) + (details?.comments ?: emptyList())).sortedByDescending { it.createdAt },
                    )
                    inlineDraft = ""
                    inlineLine = ""
                    inlineWriteState = LoadState.Idle
                }
                .onFailure {
                    inlineWriteState = LoadState.Failed(it.message ?: "Failed to post inline comment.")
                }
        }
    }

    fun aiDraftId(
        owner: String,
        repo: String,
        pullNumber: Int,
        path: String,
        commentId: Long,
        commentBody: String,
    ): String {
        val commentKey = if (commentId > 0L) commentId.toString() else commentBody.hashCode().toString()
        return "$owner/$repo#$pullNumber:${path.ifBlank { "pull" }}:$commentKey"
    }

    fun draftFixFromComment(comment: com.bniladridas.diff.model.PullComment, pullDetails: PullDetails) {
        val pull = selectedPull
        val path = comment.path.orEmpty()
        val file = pullDetails.files.firstOrNull { it.filename == path }
            ?: pullDetails.files.firstOrNull { it.filename.endsWith(path) || path.endsWith(it.filename) }
        val draftId = aiDraftId(
            owner = owner.trim(),
            repo = repo.trim(),
            pullNumber = pull?.number ?: 0,
            path = file?.filename ?: path,
            commentId = comment.id,
            commentBody = comment.body,
        )
        aiDrafts.firstOrNull { it.id == draftId }?.let { savedDraft ->
            draftFixText = savedDraft.originalContent
            generatedDraftFix = savedDraft.draftContent
            reviewDraft = savedDraft.summary.ifBlank { "Addressed review feedback on ${savedDraft.path}." }
            activeAiDraftId = savedDraft.id
            selectedFileName = savedDraft.path
            inlineFileName = savedDraft.path
            inlineLine = comment.line?.toString().orEmpty()
            activeTab = WorkspaceTab.Discussion
            accountStatus = "Saved Draft Fix restored"
            return
        }
        selectedFileName = file?.filename ?: path
        inlineFileName = file?.filename ?: path
        inlineLine = comment.line?.toString().orEmpty()
        draftFixText = buildString {
            appendLine("Review fix draft")
            appendLine()
            appendLine("File: ${file?.filename ?: path.ifBlank { "unknown" }}")
            comment.line?.let { appendLine("Line: $it") }
            appendLine()
            appendLine("Reviewer note:")
            appendLine(comment.body.ifBlank { "No comment body." }.trim())
            if (file != null && file.patch.isNotBlank()) {
                appendLine()
                appendLine("Relevant diff:")
                appendLine(file.patch.take(1600))
            }
            appendLine()
            appendLine("Suggested next step:")
            appendLine("Open the diff, make the smallest code change that satisfies the reviewer note, then reply with what changed.")
        }
        generatedDraftFix = ""
        activeAiDraftId = draftId
        draftFixState = LoadState.Idle
        reviewDraft = "Addressed review feedback on ${file?.filename ?: path}."
        activeTab = WorkspaceTab.Discussion
    }

    fun generateDraftFix() {
        val context = draftFixText
        val pull = selectedPull
        scope.launch {
            draftFixState = LoadState.Loading
            GeminiApi.generateDraftFix(geminiApiKey, context)
                .onSuccess {
                    generatedDraftFix = it
                    if (pull != null && context.isNotBlank()) {
                        val path = inlineFileName ?: selectedFileName.orEmpty()
                        val draft = AiDraft(
                            id = activeAiDraftId ?: aiDraftId(
                                owner = owner.trim(),
                                repo = repo.trim(),
                                pullNumber = pull.number,
                                path = path,
                                commentId = 0L,
                                commentBody = context,
                            ),
                            owner = owner.trim(),
                            repo = repo.trim(),
                            pullNumber = pull.number,
                            pullTitle = pull.title,
                            branch = pull.head,
                            path = path,
                            commentId = details?.comments?.firstOrNull { comment ->
                                comment.kind == CommentKind.Review && comment.path == path && context.contains(comment.body.take(80))
                            }?.id ?: 0L,
                            commentBody = details?.comments?.firstOrNull { comment ->
                                comment.kind == CommentKind.Review && comment.path == path && context.contains(comment.body.take(80))
                            }?.body.orEmpty(),
                            originalContent = context,
                            draftContent = it,
                            summary = "Addressed review feedback on ${path.ifBlank { "the pull request" }}.",
                            updatedAt = System.currentTimeMillis(),
                        )
                        saveAiDraft(draft)
                    }
                    draftFixState = LoadState.Idle
                }
                .onFailure {
                    draftFixState = LoadState.Failed(it.message ?: "Failed to generate Draft Fix.")
                }
        }
    }

    LaunchedEffect(githubToken) {
        GitHubApi.setAuthToken(githubToken)
        if (githubToken.isNotBlank() && githubLogin == null) {
            verifyGitHubToken()
        }
    }

    LaunchedEffect(authCallbackUri) {
        val uri = authCallbackUri ?: return@LaunchedEffect
        finishSupabaseGitHubSignIn(uri)
    }

    LaunchedEffect(highlightedDiffPath, highlightedDiffLine, highlightedDiffStartLine, highlightedDiffSide, highlightedDiffStartSide) {
        if (highlightedDiffPath == null || highlightedDiffLine == null) return@LaunchedEffect
        delay(4_000)
        highlightedDiffPath = null
        highlightedDiffLine = null
        highlightedDiffStartLine = null
        highlightedDiffSide = null
        highlightedDiffStartSide = null
    }

    fun openPull(pull: PullRequest) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        val requestId = detailRequestId + 1
        detailRequestId = requestId
        selectedPull = pull
        selectedBranch = null
        branchDetails = null
        branchDetailState = LoadState.Idle
        managementTitle = pull.title
        managementBody = pull.body
        managementLabels = pull.labels.joinToString(", ") { it.name }
        managementWriteState = LoadState.Idle
        managementExpanded = false
        mergeTitle = pull.title
        mergeMessage = ""
        mergeMethod = "squash"
        mergeWriteState = LoadState.Idle
        updateBranchState = LoadState.Idle
        deleteBranchState = LoadState.Idle
        details = null
        selectedFileName = null
        highlightedDiffPath = null
        highlightedDiffLine = null
        highlightedDiffStartLine = null
        highlightedDiffSide = null
        highlightedDiffStartSide = null
        checksState = LoadState.Idle
        checksPolling = false
        checkLogs = emptyMap()
        checkLogStates = emptyMap()
        activeTab = WorkspaceTab.Diff
        pane = MobilePane.Workspace
        scope.launch {
            detailState = LoadState.Loading
            val currentPull = GitHubApi.fetchPullRequest(safeOwner, safeRepo, pull.number)
                .getOrElse { pull }
            if (requestId != detailRequestId) return@launch
            selectedPull = currentPull
            managementTitle = currentPull.title
            managementBody = currentPull.body
            managementLabels = currentPull.labels.joinToString(", ") { it.name }
            mergeTitle = currentPull.title
            GitHubApi.fetchPullDetails(safeOwner, safeRepo, currentPull)
                .onSuccess {
                    if (requestId != detailRequestId) return@onSuccess
                    details = it
                    selectedFileName = it.files.firstOrNull()?.filename
                    detailState = LoadState.Idle
                    checksState = LoadState.Loading
                    GitHubApi.fetchPullChecks(safeOwner, safeRepo, currentPull)
                        .onSuccess { checks ->
                            if (requestId != detailRequestId) return@onSuccess
                            details = details?.copy(checks = checks)
                            checksState = LoadState.Idle
                        }
                        .onFailure { error ->
                            if (requestId != detailRequestId) return@onFailure
                            checksState = LoadState.Failed(error.message ?: "Failed to refresh checks.")
                        }
                }
                .onFailure {
                    if (requestId != detailRequestId) return@onFailure
                    detailState = LoadState.Failed(it.message ?: "Could not load pull request details.")
                }
        }
    }

    fun loadPulls(keepSelection: Boolean = false) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        if (safeOwner.isEmpty() || safeRepo.isEmpty()) {
            listState = LoadState.Failed("Owner and repo are required.")
            return
        }
        val nextRepoKey = "$safeOwner/$safeRepo:${filter.apiValue}"
        val preserveSelection = keepSelection && nextRepoKey == loadedRepoKey

        scope.launch {
            val requestId = listRequestId + 1
            listRequestId = requestId
            listState = LoadState.Loading
            if (!preserveSelection) {
                selectedPull = null
                details = null
                selectedFileName = null
                pane = MobilePane.Pulls
            }
            val result = GitHubApi.fetchPulls(safeOwner, safeRepo, filter)
            result
                .onSuccess { nextPulls ->
                    if (requestId != listRequestId) return@onSuccess
                    pulls = nextPulls
                    rememberRepo(safeOwner, safeRepo)
                    loadedRepoKey = nextRepoKey
                    listState = LoadState.Idle
                    val preservedPull = selectedPull?.let { selected ->
                        nextPulls.firstOrNull { it.number == selected.number }
                    }
                    val savedPullToOpen = pendingSavedPullNumber?.let { number ->
                        nextPulls.firstOrNull { it.number == number }
                    }
                    if (pendingSavedPullNumber != null && savedPullToOpen == null) {
                        pendingSavedPullNumber = null
                    }
                    when {
                        savedPullToOpen != null -> {
                            pendingSavedPullNumber = null
                            openPull(savedPullToOpen)
                        }
                        preserveSelection && preservedPull != null -> selectedPull = preservedPull
                    }
                }
                .onFailure {
                    if (requestId != listRequestId) return@onFailure
                    listState = LoadState.Failed(it.message ?: "Could not load pull requests.")
                }
        }
    }

    fun updateSelectedPull() {
        val pull = selectedPull ?: run {
            managementWriteState = LoadState.Failed("Select a pull request before updating it.")
            return
        }
        scope.launch {
            managementWriteState = LoadState.Loading
            GitHubApi.updatePullRequest(
                owner = owner.trim(),
                repo = repo.trim(),
                number = pull.number,
                title = managementTitle,
                body = managementBody,
            )
                .mapCatching { updated ->
                    val labels = GitHubApi.updateIssueLabels(
                        owner = owner.trim(),
                        repo = repo.trim(),
                        number = pull.number,
                        labels = managementLabels.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    ).getOrThrow()
                    updated.copy(labels = labels)
                }
                .onSuccess { updatedPull ->
                    selectedPull = updatedPull
                    pulls = pulls.map { if (it.number == updatedPull.number) updatedPull else it }
                    savedPulls = savedPulls.map {
                        if (it.owner == owner && it.repo == repo && it.number == updatedPull.number) {
                            it.copy(title = updatedPull.title, state = updatedPull.state)
                        } else {
                            it
                        }
                    }
                    localPreferences.saveSavedPulls(savedPulls)
                    pushSupabasePreferences()
                    managementTitle = updatedPull.title
                    managementBody = updatedPull.body
                    managementLabels = updatedPull.labels.joinToString(", ") { it.name }
                    managementWriteState = LoadState.Idle
                }
                .onFailure {
                    managementWriteState = LoadState.Failed(it.message ?: "Failed to update pull request.")
                }
        }
    }

    fun refreshChecks(poll: Boolean = false) {
        val pull = selectedPull ?: run {
            checksState = LoadState.Failed("Select a pull request before refreshing checks.")
            return
        }
        scope.launch {
            checksState = LoadState.Loading
            GitHubApi.fetchPullChecks(owner.trim(), repo.trim(), pull)
                .onSuccess { checks ->
                    details = details?.copy(checks = checks)
                    checksState = LoadState.Idle
                    if (poll && checks.any { it.conclusion == null && it.status != "completed" }) {
                        delay(8_000)
                        if (checksPolling && selectedPull?.number == pull.number) {
                            refreshChecks(poll = true)
                        }
                    } else if (poll) {
                        checksPolling = false
                    }
                }
                .onFailure {
                    checksState = LoadState.Failed(it.message ?: "Failed to refresh checks.")
                    if (poll) checksPolling = false
                }
        }
    }

    fun toggleCheckPolling() {
        checksPolling = !checksPolling
        if (checksPolling) {
            refreshChecks(poll = true)
        }
    }

    fun loadCheckLog(check: CheckRun) {
        if (checkLogs.containsKey(check.id) || checkLogStates[check.id] == LoadState.Loading) return
        scope.launch {
            checkLogStates = checkLogStates + (check.id to LoadState.Loading)
            GitHubApi.fetchCheckLog(owner.trim(), repo.trim(), check)
                .onSuccess { log ->
                    checkLogs = checkLogs + (check.id to log)
                    checkLogStates = checkLogStates + (check.id to LoadState.Idle)
                }
                .onFailure {
                    checkLogStates = checkLogStates + (check.id to LoadState.Failed(it.message ?: "Failed to load check log."))
                }
        }
    }

    fun clearCheckLog(check: CheckRun) {
        checkLogs = checkLogs - check.id
        checkLogStates = checkLogStates - check.id
    }

    fun mergeSelectedPull() {
        val pull = selectedPull ?: run {
            mergeWriteState = LoadState.Failed("Select a pull request before merging it.")
            return
        }
        scope.launch {
            mergeWriteState = LoadState.Loading
            GitHubApi.mergePullRequest(
                owner = owner.trim(),
                repo = repo.trim(),
                number = pull.number,
                method = mergeMethod,
                title = mergeTitle,
                message = mergeMessage,
            )
                .onSuccess {
                    val mergedPull = pull.copy(state = "closed", merged = true)
                    selectedPull = mergedPull
                    pulls = pulls.map { if (it.number == mergedPull.number) mergedPull else it }
                    savedPulls = savedPulls.map {
                        if (it.owner == owner && it.repo == repo && it.number == mergedPull.number) {
                            it.copy(state = mergedPull.state)
                        } else {
                            it
                        }
                    }
                    localPreferences.saveSavedPulls(savedPulls)
                    pushSupabasePreferences()
                    mergeWriteState = LoadState.Idle
                }
                .onFailure {
                    mergeWriteState = LoadState.Failed(it.message ?: "Failed to merge pull request.")
                }
        }
    }

    fun updateSelectedPullBranch() {
        val pull = selectedPull ?: run {
            updateBranchState = LoadState.Failed("Select a pull request before updating the branch.")
            return
        }
        scope.launch {
            updateBranchState = LoadState.Loading
            GitHubApi.updatePullBranch(
                owner = owner.trim(),
                repo = repo.trim(),
                number = pull.number,
            )
                .onSuccess {
                    updateBranchState = LoadState.Idle
                    openPull(pull)
                }
                .onFailure {
                    updateBranchState = LoadState.Failed(it.message ?: "Failed to update branch.")
                }
        }
    }

    fun deleteSelectedHeadBranch() {
        val pull = selectedPull ?: run {
            deleteBranchState = LoadState.Failed("Select a pull request before deleting the branch.")
            return
        }
        scope.launch {
            deleteBranchState = LoadState.Loading
            GitHubApi.deleteBranch(
                owner = owner.trim(),
                repo = repo.trim(),
                branch = pull.head,
            )
                .onSuccess {
                    branches = branches.filterNot { it.name == pull.head }
                    deleteBranchState = LoadState.Idle
                }
                .onFailure {
                    deleteBranchState = LoadState.Failed(it.message ?: "Failed to delete branch.")
                }
        }
    }

    fun loadBranchComparison(branch: Branch) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        val base = defaultBranch.ifBlank { "main" }
        val requestId = detailRequestId + 1
        detailRequestId = requestId
        branchDetails = null
        selectedFileName = null
        activeTab = WorkspaceTab.Diff
        scope.launch {
            branchDetailState = LoadState.Loading
            GitHubApi.fetchBranchComparison(safeOwner, safeRepo, base, branch.name)
                .onSuccess {
                    if (requestId != detailRequestId) return@onSuccess
                    branchDetails = it
                    selectedFileName = it.files.firstOrNull()?.filename
                    branchDetailState = LoadState.Idle
                }
                .onFailure {
                    if (requestId != detailRequestId) return@onFailure
                    branchDetailState = LoadState.Failed(it.message ?: "Could not load branch comparison.")
                }
        }
    }

    fun openBranchWorkspace(branch: Branch) {
        activeTab = WorkspaceTab.Diff
        selectedPull = null
        details = null
        branchDetails = null
        selectedFileName = null
        selectedRepoFilePath = null
        repoFileContent = null
        repoEditContent = ""
        conflictResolutionHint = null
        conflictBaseContent = null
        conflictBaseRef = null
        selectedBranch = branch
        pane = MobilePane.Workspace
        loadBranchComparison(branch)
    }

    fun orderBranchesForNetwork(
        repoBranches: List<Branch>,
        activePulls: List<PullRequest>,
    ): List<Branch> {
        val repoKey = "${owner.trim()}/${repo.trim()}"
        val activeHeads = activePulls
            .filter { pull ->
                pull.state == "open" &&
                    pull.head.isNotBlank() &&
                    pull.headRepoFullName.equals(repoKey, ignoreCase = true)
            }
            .map { it.head }
            .distinct()
        if (activeHeads.isEmpty()) return repoBranches
        val activeBranches = activeHeads.mapNotNull { head ->
            repoBranches.firstOrNull { branch -> branch.name == head }
        }
        val activeNames = activeBranches.map { it.name }.toSet()
        return activeBranches + repoBranches.filterNot { it.name in activeNames }
    }

    fun loadBranches(
        autoSelect: Boolean = false,
        openWorkspace: Boolean = false,
    ) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        if (safeOwner.isEmpty() || safeRepo.isEmpty()) {
            branchesState = LoadState.Failed("Owner and repo are required.")
            return
        }
        scope.launch {
            val requestId = branchesRequestId + 1
            branchesRequestId = requestId
            branchesState = LoadState.Loading
            defaultBranch = GitHubApi.fetchDefaultBranch(safeOwner, safeRepo)
                .getOrElse { defaultBranch.ifBlank { "main" } }
            GitHubApi.fetchBranches(safeOwner, safeRepo)
                .onSuccess {
                    if (requestId != branchesRequestId) return@onSuccess
                    rememberRepo(safeOwner, safeRepo)
                    val openPulls = GitHubApi.fetchPulls(safeOwner, safeRepo, PullFilter.Open)
                        .getOrElse {
                            pulls.filter { pull -> pull.state == "open" }
                        }
                    val orderedBranches = orderBranchesForNetwork(it, openPulls)
                    branches = orderedBranches
                    if (autoSelect) {
                        val nextBranch = orderedBranches.firstOrNull()
                        if (openWorkspace && nextBranch != null) {
                            openBranchWorkspace(nextBranch)
                        } else {
                            selectedBranch = nextBranch
                        }
                    } else {
                        selectedBranch = selectedBranch
                            ?.let { current -> orderedBranches.firstOrNull { branch -> branch.name == current.name } }
                    }
                    branchesState = LoadState.Idle
                }
                .onFailure {
                    if (requestId != branchesRequestId) return@onFailure
                    branchesState = LoadState.Failed(it.message ?: "Could not load branches.")
                }
        }
    }

    fun loadRepoFile(
        path: String,
        ref: String = selectedBranch?.name ?: "HEAD",
        baseRefForConflict: String? = conflictBaseRef,
    ) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        scope.launch {
            val requestId = repoFileRequestId + 1
            repoFileRequestId = requestId
            repoFileState = LoadState.Loading
            selectedRepoFilePath = path
            GitHubApi.fetchRepoFile(safeOwner, safeRepo, path, ref)
                .onSuccess {
                    if (requestId != repoFileRequestId) return@onSuccess
                    repoFileContent = it
                    repoEditContent = it.content
                    repoCommitMessage = "Update ${it.path}"
                    repoCreateMode = false
                    repoWriteState = LoadState.Idle
                    repoFileState = LoadState.Idle
                    if (baseRefForConflict != null) {
                        conflictBaseState = LoadState.Loading
                        GitHubApi.fetchRepoFile(safeOwner, safeRepo, path, baseRefForConflict)
                            .onSuccess { baseFile ->
                                if (requestId != repoFileRequestId) return@onSuccess
                                conflictBaseContent = baseFile
                                conflictBaseState = LoadState.Idle
                            }
                            .onFailure { error ->
                                if (requestId != repoFileRequestId) return@onFailure
                                conflictBaseContent = null
                                conflictBaseState = LoadState.Failed(error.message ?: "Could not load base file.")
                            }
                    } else {
                        conflictBaseContent = null
                        conflictBaseState = LoadState.Idle
                    }
                }
                .onFailure {
                    if (requestId != repoFileRequestId) return@onFailure
                    repoFileState = LoadState.Failed(it.message ?: "Could not load file.")
                }
        }
    }

    fun commitSelectedRepoFile() {
        val file = repoFileContent ?: run {
            repoWriteState = LoadState.Failed("Select a repository file before committing.")
            return
        }
        val branch = selectedBranch ?: run {
            repoWriteState = LoadState.Failed("Select or create a branch before committing.")
            return
        }
        scope.launch {
            repoWriteState = LoadState.Loading
            GitHubApi.commitRepoFile(
                owner = owner.trim(),
                repo = repo.trim(),
                file = file,
                branch = branch.name,
                content = repoEditContent,
                message = repoCommitMessage,
            )
                .onSuccess {
                    repoFileContent = it
                    repoEditContent = it.content
                    repoWriteState = LoadState.Idle
                    if (pullTitle.isBlank()) {
                        pullTitle = repoCommitMessage.ifBlank { "Update ${it.path}" }
                    }
                }
                .onFailure {
                    repoWriteState = LoadState.Failed(it.message ?: "Failed to commit file.")
                }
        }
    }

    fun createSelectedRepoFile() {
        val branch = selectedBranch ?: run {
            repoCreateState = LoadState.Failed("Select or create a branch before creating a file.")
            return
        }
        scope.launch {
            repoCreateState = LoadState.Loading
            GitHubApi.createRepoFile(
                owner = owner.trim(),
                repo = repo.trim(),
                path = repoNewFilePath,
                branch = branch.name,
                content = repoNewFileContent,
                message = repoCommitMessage.ifBlank { "Create ${repoNewFilePath.trim()}" },
            )
                .onSuccess { created ->
                    repoFileContent = created
                    selectedRepoFilePath = created.path
                    repoEditContent = created.content
                    repoNewFilePath = ""
                    repoNewFileContent = ""
                    repoCreateMode = false
                    repoCreateState = LoadState.Idle
                    repoWriteState = LoadState.Idle
                    repoFiles = (
                        listOf(
                            RepoTreeItem(
                                path = created.path,
                                type = "blob",
                                sha = created.sha,
                                size = created.content.length,
                            ),
                        ) + repoFiles
                    ).distinctBy { it.path }.sortedBy { it.path }
                }
                .onFailure {
                    repoCreateState = LoadState.Failed(it.message ?: "Failed to create file.")
                }
        }
    }

    fun loadRepoTree(
        ref: String = selectedBranch?.name ?: "HEAD",
        preferredPath: String? = null,
    ) {
        val safeOwner = owner.trim()
        val safeRepo = repo.trim()
        if (safeOwner.isEmpty() || safeRepo.isEmpty()) {
            repoTreeState = LoadState.Failed("Owner and repo are required.")
            return
        }
        scope.launch {
            val requestId = repoTreeRequestId + 1
            repoTreeRequestId = requestId
            repoTreeState = LoadState.Loading
            repoFileContent = null
            selectedRepoFilePath = null
            conflictBaseContent = null
            conflictBaseState = LoadState.Idle
            GitHubApi.fetchRepoTree(safeOwner, safeRepo, ref)
                .onSuccess { files ->
                    if (requestId != repoTreeRequestId) return@onSuccess
                    rememberRepo(safeOwner, safeRepo)
                    repoFiles = files
                    repoTreeState = LoadState.Idle
                    val fileToOpen = preferredPath
                        ?.let { path -> files.firstOrNull { it.path == path } }
                        ?: files.firstOrNull()
                    fileToOpen?.let { loadRepoFile(it.path, ref) }
                }
                .onFailure {
                    if (requestId != repoTreeRequestId) return@onFailure
                    repoTreeState = LoadState.Failed(it.message ?: "Could not load repository files.")
                }
        }
    }

    fun startConflictResolution(pull: PullRequest) {
        val changedFiles = details?.files.orEmpty()
        val targetPath = selectedFileName
            ?.let { selected -> changedFiles.firstOrNull { it.filename == selected }?.filename }
            ?: changedFiles.firstOrNull()?.filename
        selectedBranch = Branch(
            name = pull.head,
            sha = pull.headSha,
            protected = false,
        )
        pane = MobilePane.Pulls
        streamMode = StreamMode.Code
        repoCreateMode = false
        repoCommitMessage = targetPath
            ?.let { "Resolve conflicts in $it for #${pull.number}" }
            ?: "Resolve conflicts for #${pull.number}"
        pullBaseBranch = pull.base
        pullTitle = pull.title
        pullBody = pull.body
        conflictBaseRef = pull.base
        conflictBaseContent = null
        conflictBaseState = LoadState.Idle
        conflictResolutionHint = "Resolving #${pull.number} on ${pull.head}. Edit the conflicted files and commit to the PR branch."
        loadRepoTree(pull.head, targetPath)
    }

    fun workPullBranchInCode(pull: PullRequest) {
        val targetPath = selectedFileName
            ?: details?.files?.firstOrNull()?.filename
        selectedBranch = Branch(
            name = pull.head,
            sha = pull.headSha,
            protected = false,
        )
        pane = MobilePane.Pulls
        streamMode = StreamMode.Code
        activeTab = WorkspaceTab.Diff
        repoCreateMode = false
        repoNewBranchName = ""
        pullBaseBranch = pull.base
        pullTitle = pull.title
        pullBody = pull.body
        conflictResolutionHint = null
        conflictBaseContent = null
        conflictBaseRef = null
        loadRepoTree(pull.head, targetPath)
    }

    fun createEditBranch() {
        val source = selectedBranch ?: branches.firstOrNull() ?: run {
            branchWriteState = LoadState.Failed("Load branches before creating a branch.")
            return
        }
        scope.launch {
            branchWriteState = LoadState.Loading
            GitHubApi.createBranch(
                owner = owner.trim(),
                repo = repo.trim(),
                name = repoNewBranchName,
                sourceSha = source.sha,
            )
                .onSuccess { branch ->
                    branches = (listOf(branch) + branches).distinctBy { it.name }
                    selectedBranch = branch
                    repoNewBranchName = ""
                    branchWriteState = LoadState.Idle
                    loadRepoTree(branch.name)
                }
                .onFailure {
                    branchWriteState = LoadState.Failed(it.message ?: "Failed to create branch.")
                }
        }
    }

    fun openPullFromBranch() {
        val source = selectedBranch ?: run {
            pullWriteState = LoadState.Failed("Select or create a source branch first.")
            return
        }
        scope.launch {
            pullWriteState = LoadState.Loading
            GitHubApi.createPullRequest(
                owner = owner.trim(),
                repo = repo.trim(),
                title = pullTitle,
                body = pullBody,
                head = source.name,
                base = pullBaseBranch,
            )
                .onSuccess { pull ->
                    pulls = (listOf(pull) + pulls).distinctBy { it.number }
                    pullWriteState = LoadState.Idle
                    pullTitle = ""
                    pullBody = ""
                    openPull(pull)
                }
                .onFailure {
                    pullWriteState = LoadState.Failed(it.message ?: "Failed to open pull request.")
                }
        }
    }

    LaunchedEffect(filter) {
        loadPulls()
    }

    LaunchedEffect(streamMode) {
        when (streamMode) {
            StreamMode.Pulls -> loadPulls(keepSelection = true)
            StreamMode.Branches -> loadBranches(autoSelect = false)
            StreamMode.Code -> {
                if (branches.isEmpty()) loadBranches()
                loadRepoTree(selectedBranch?.name ?: "HEAD")
            }
        }
    }

    LaunchedEffect(repoSwitchRequest) {
        if (repoSwitchRequest == 0) return@LaunchedEffect
        when (streamMode) {
            StreamMode.Pulls -> loadPulls()
            StreamMode.Branches -> loadBranches(autoSelect = false)
            StreamMode.Code -> {
                if (branches.isEmpty()) loadBranches()
                loadRepoTree(selectedBranch?.name ?: "HEAD")
            }
        }
    }

    BackHandler {
        when {
            showAccount -> showAccount = false
            pane == MobilePane.Workspace && activeTab != WorkspaceTab.Diff -> activeTab = WorkspaceTab.Diff
            pane == MobilePane.Workspace -> {
                val returningFromBranch = selectedPull == null && selectedBranch != null
                pane = MobilePane.Pulls
                streamMode = if (returningFromBranch) StreamMode.Branches else StreamMode.Pulls
            }
            streamMode != StreamMode.Pulls -> {
                streamMode = StreamMode.Pulls
                selectedBranch = null
                selectedRepoFilePath = null
                repoFileContent = null
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastRootBackAt <= 2_000L) {
                    activity?.finish()
                } else {
                    lastRootBackAt = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showAccount) {
        AccountDialog(
            token = githubToken,
            geminiKey = geminiApiKey,
            supabaseConfig = supabaseConfig,
            login = githubLogin,
            status = accountStatus,
            syncState = preferencesSyncState,
            onDismiss = { showAccount = false },
            onSaveToken = { saveGitHubToken(it) },
            onClearToken = { clearGitHubToken() },
            onSaveGeminiKey = { saveGeminiApiKey(it) },
            onClearGeminiKey = { clearGeminiApiKey() },
            onSaveSupabaseConfig = { saveSupabaseConfig(it) },
            onClearSupabaseConfig = { clearSupabaseConfig() },
            onStartSupabaseGitHubSignIn = { startSupabaseGitHubSignIn(it) },
            onSignOutSupabase = { signOutSupabase(it) },
            onPullSupabasePreferences = {
                saveSupabaseConfig(it)
                pullSupabasePreferences(it)
            },
            onPushSupabasePreferences = {
                saveSupabaseConfig(it)
                pushSupabasePreferences(it)
            },
            onClearSavedState = { clearSavedAppState() },
            onVerifyToken = { verifyGitHubToken(it) },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandMark()
                    Spacer(Modifier.weight(1f))
                    if (pane == MobilePane.Workspace) {
                        HeaderButton("Pulls") {
                            pane = MobilePane.Pulls
                            streamMode = StreamMode.Pulls
                        }
                    }
                    HeaderButton(githubLogin ?: if (githubToken.isBlank()) "Sign In" else "Account") {
                        showAccount = true
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = contentListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (pane == MobilePane.Pulls) {
                item {
                    RepoPanel(
                        owner = owner,
                        repo = repo,
                        defaultRepo = defaultRepo,
                        recentRepos = recentRepos,
                        savedPulls = savedPulls,
                        aiDrafts = aiDrafts,
                        filter = filter,
                        streamMode = streamMode,
                        isBranchWorkspace = selectedBranch != null && streamMode == StreamMode.Code,
                        onSelectRepo = { selectRepo(it) },
                        onSetDefaultRepo = { setDefaultRepo(RepoRef(owner.trim(), repo.trim())) },
                        onResetDefaultRepo = {
                            setDefaultRepo(fallbackRepo)
                            selectRepo(fallbackRepo)
                        },
                        onOpenSavedPull = { openSavedPull(it) },
                        onOpenAiDraft = { openAiDraft(it) },
                        onFilterChange = { filter = it },
                        onStreamModeChange = { nextMode ->
                            if (nextMode == StreamMode.Branches) {
                                pane = MobilePane.Pulls
                                selectedBranch = null
                            } else if (nextMode == StreamMode.Code) {
                                activeTab = WorkspaceTab.Diff
                                selectedPull = null
                                selectedBranch = null
                                details = null
                                selectedFileName = null
                                selectedRepoFilePath = null
                                repoFileContent = null
                                repoEditContent = ""
                                conflictResolutionHint = null
                                conflictBaseContent = null
                                conflictBaseRef = null
                            }
                            streamMode = nextMode
                        },
                        onLoad = {
                            when (streamMode) {
                                StreamMode.Pulls -> loadPulls(keepSelection = true)
                                StreamMode.Branches -> loadBranches(autoSelect = false)
                                StreamMode.Code -> {
                                    if (branches.isEmpty()) loadBranches()
                                    loadRepoTree(selectedBranch?.name ?: "HEAD")
                                }
                            }
                        },
                    )
                }

                when (streamMode) {
                    StreamMode.Pulls -> pullStreamItems(
                        pulls = pulls,
                        selectedPull = selectedPull,
                        state = listState,
                        filter = filter,
                        onOpenPull = { openPull(it) },
                    )
                    StreamMode.Branches -> branchItems(
                        branches = branches,
                        selectedBranch = selectedBranch,
                        defaultBranch = defaultBranch,
                        state = branchesState,
                        onSelectBranch = {
                            activeTab = WorkspaceTab.Diff
                            selectedPull = null
                            details = null
                            branchDetails = null
                            selectedFileName = null
                            selectedRepoFilePath = null
                            repoFileContent = null
                            repoEditContent = ""
                            conflictResolutionHint = null
                            conflictBaseContent = null
                            conflictBaseRef = null
                            selectedBranch = it
                            pane = MobilePane.Workspace
                            loadBranchComparison(it)
                        },
                    )
                    StreamMode.Code -> codeItems(
                        files = repoFiles,
                        selectedPath = selectedRepoFilePath,
                        treeState = repoTreeState,
                        fileState = repoFileState,
                        fileContent = repoFileContent,
                        canWrite = githubToken.isNotBlank(),
                        branchName = selectedBranch?.name,
                        branchState = branchWriteState,
                        newBranchName = repoNewBranchName,
                        pullTitle = pullTitle,
                        pullBody = pullBody,
                        pullBaseBranch = pullBaseBranch,
                        pullState = pullWriteState,
                        createMode = repoCreateMode,
                        newFilePath = repoNewFilePath,
                        newFileContent = repoNewFileContent,
                        createState = repoCreateState,
                        conflictHint = conflictResolutionHint,
                        conflictBaseContent = conflictBaseContent,
                        conflictBaseState = conflictBaseState,
                        conflictBaseRef = conflictBaseRef,
                        editContent = repoEditContent,
                        commitMessage = repoCommitMessage,
                        writeState = repoWriteState,
                        onNewBranchNameChange = { repoNewBranchName = it },
                        onCreateBranch = { createEditBranch() },
                        onPullTitleChange = { pullTitle = it },
                        onPullBodyChange = { pullBody = it },
                        onPullBaseBranchChange = { pullBaseBranch = it },
                        onOpenPull = { openPullFromBranch() },
                        onCreateModeChange = { repoCreateMode = it },
                        onNewFilePathChange = { repoNewFilePath = it },
                        onNewFileContentChange = { repoNewFileContent = it },
                        onCreateFile = { createSelectedRepoFile() },
                        onEditContentChange = { repoEditContent = it },
                        onCommitMessageChange = { repoCommitMessage = it },
                        onCommitFile = { commitSelectedRepoFile() },
                        onSelectFile = { loadRepoFile(it.path) },
                    )
                }
            } else {
                selectedPull?.let { pull ->
                    item { WorkspaceHeader(pull) }
                    item {
                        WorkspaceActions(
                            saved = savedPulls.any { it.owner == owner && it.repo == repo && it.number == pull.number },
                            onToggleSaved = { toggleSavedPull(pull) },
                            canOpenBranch = pull.state == "open",
                            onOpenBranch = { workPullBranchInCode(pull) },
                        )
                    }
                    item {
                        PullManagementPanel(
                            canWrite = githubToken.isNotBlank(),
                            title = managementTitle,
                            body = managementBody,
                            labels = managementLabels,
                            state = managementWriteState,
                            mergeMethod = mergeMethod,
                            mergeTitle = mergeTitle,
                            mergeMessage = mergeMessage,
                            mergeState = mergeWriteState,
                            updateBranchState = updateBranchState,
                            deleteBranchState = deleteBranchState,
                            headBranch = pull.head,
                            merged = pull.merged,
                            mergeable = pull.mergeable,
                            mergeableState = pull.mergeableState,
                            pullOpen = pull.state == "open",
                            canResolveConflicts = details?.files?.isNotEmpty() == true,
                            expanded = managementExpanded,
                            onExpandedChange = { managementExpanded = it },
                            onTitleChange = { managementTitle = it },
                            onBodyChange = { managementBody = it },
                            onLabelsChange = { managementLabels = it },
                            onSave = { updateSelectedPull() },
                            onMergeMethodChange = { mergeMethod = it },
                            onMergeTitleChange = { mergeTitle = it },
                            onMergeMessageChange = { mergeMessage = it },
                            onMerge = { mergeSelectedPull() },
                            onUpdateBranch = { updateSelectedPullBranch() },
                            onDeleteBranch = { deleteSelectedHeadBranch() },
                            onResolveConflicts = { startConflictResolution(pull) },
                        )
                    }
                    item {
                        WorkspaceTabs(
                            activeTab = activeTab,
                        counts = mapOf(
                            WorkspaceTab.Diff to (details?.files?.size ?: 0),
                            WorkspaceTab.Discussion to (details?.comments?.size ?: 0),
                                WorkspaceTab.Checks to (details?.checks?.size ?: 0),
                                WorkspaceTab.History to (details?.let { historyEntryCount(it) } ?: 0),
                            ),
                            onSelect = { activeTab = it },
                        )
                    }

                    when (val state = detailState) {
                        LoadState.Loading -> item { LoadingRow("Loading review workspace") }
                        is LoadState.Failed -> item { ErrorPanel(state.message) }
                        LoadState.Idle -> {
                            details?.let { pullDetails ->
                                when (activeTab) {
                                    WorkspaceTab.Diff -> diffItems(
                                        details = pullDetails,
                                        selectedFileName = selectedFileName,
                                        highlightedPath = highlightedDiffPath,
                                        highlightedLine = highlightedDiffLine,
                                        highlightedStartLine = highlightedDiffStartLine,
                                        highlightedSide = highlightedDiffSide,
                                        highlightedStartSide = highlightedDiffStartSide,
                                        onSelectedFileName = { selectedFileName = it },
                                    )
                                    WorkspaceTab.Discussion -> discussionItems(
                                        details = pullDetails,
                                        canWrite = githubToken.isNotBlank(),
                                        draft = discussionDraft,
                                        writeState = discussionWriteState,
                                        onDraftChange = { discussionDraft = it },
                                        onSubmitDiscussion = { postDiscussionComment(pull) },
                                        reviewDraft = reviewDraft,
                                        reviewWriteState = reviewWriteState,
                                        onReviewDraftChange = { reviewDraft = it },
                                        onSubmitReview = { event -> submitPullReview(pull, event) },
                                        inlineDraft = inlineDraft,
                                        inlineLine = inlineLine,
                                        inlineFileName = inlineFileName ?: selectedFileName,
                                        inlineWriteState = inlineWriteState,
                                        onInlineDraftChange = { inlineDraft = it },
                                        onInlineLineChange = { inlineLine = it },
                                        onInlineFileChange = { inlineFileName = it },
                                        onSubmitInline = { postInlineComment(pull) },
                                        draftFixText = draftFixText,
                                        generatedDraftFix = generatedDraftFix,
                                        draftFixState = draftFixState,
                                        savedDraftsCount = aiDrafts.size,
                                        activeAiDraftId = activeAiDraftId,
                                        canGenerateDraftFix = geminiApiKey.isNotBlank(),
                                        onClearDraftFix = {
                                            draftFixText = ""
                                            generatedDraftFix = ""
                                            activeAiDraftId = null
                                        },
                                        onDeleteDraftFix = { deleteActiveAiDraft() },
                                        onGenerateDraftFix = { generateDraftFix() },
                                        onUseDraftFixAsReview = { reviewDraft = generatedDraftFix.ifBlank { draftFixText } },
                                        onOpenAnnotation = { comment ->
                                            val path = comment.path.orEmpty()
                                            val matchingFile = pullDetails.files.firstOrNull { it.filename == path }
                                                ?: pullDetails.files.firstOrNull { it.filename.endsWith(path) || path.endsWith(it.filename) }
                                            selectedFileName = matchingFile?.filename ?: path
                                            highlightedDiffPath = selectedFileName
                                            highlightedDiffLine = comment.line
                                            highlightedDiffStartLine = comment.startLine
                                            highlightedDiffSide = comment.side
                                            highlightedDiffStartSide = comment.startSide
                                            activeTab = WorkspaceTab.Diff
                                            scope.launch {
                                                delay(120)
                                                contentListState.animateScrollToItem(pullDetails.files.size + 6)
                                            }
                                        },
                                        onDraftFix = { comment -> draftFixFromComment(comment, pullDetails) },
                                    )
                                    WorkspaceTab.Checks -> checksItems(
                                        details = pullDetails,
                                        state = checksState,
                                        polling = checksPolling,
                                        logs = checkLogs,
                                        logStates = checkLogStates,
                                        onRefresh = { refreshChecks() },
                                        onTogglePolling = { toggleCheckPolling() },
                                        onLoadLog = { check -> loadCheckLog(check) },
                                        onClearLog = { check -> clearCheckLog(check) },
                                        onOpenAnnotation = { path ->
                                            val matchingFile = pullDetails.files.firstOrNull { it.filename == path }
                                                ?: pullDetails.files.firstOrNull { it.filename.endsWith(path) || path.endsWith(it.filename) }
                                            selectedFileName = matchingFile?.filename ?: path
                                            activeTab = WorkspaceTab.Diff
                                        },
                                    )
                                    WorkspaceTab.History -> pullHistoryItems(pullDetails)
                                }
                            }
                        }
                    }
                } ?: selectedBranch?.let { branch ->
                    item { BranchWorkspaceHeader(branch = branch, baseBranch = defaultBranch) }
                    item {
                        BranchWorkspaceTabs(
                            activeTab = activeTab,
                            diffCount = branchDetails?.files?.size ?: 0,
                            historyCount = branchDetails?.commits?.size ?: 0,
                            onSelect = { activeTab = it },
                        )
                    }
                    when (val state = branchDetailState) {
                        LoadState.Loading -> item { LoadingRow("Loading branch comparison") }
                        is LoadState.Failed -> item { ErrorPanel(state.message) }
                        LoadState.Idle -> {
                            val comparison = branchDetails
                            if (comparison == null) {
                                item { EmptyPanel("Select a branch to inspect its changes.") }
                            } else {
                                when (activeTab) {
                                    WorkspaceTab.Diff -> diffItems(
                                        details = comparison,
                                        selectedFileName = selectedFileName,
                                        highlightedPath = highlightedDiffPath,
                                        highlightedLine = highlightedDiffLine,
                                        highlightedStartLine = highlightedDiffStartLine,
                                        highlightedSide = highlightedDiffSide,
                                        highlightedStartSide = highlightedDiffStartSide,
                                        onSelectedFileName = { selectedFileName = it },
                                    )
                                    WorkspaceTab.History -> branchHistoryItems(comparison)
                                    WorkspaceTab.Discussion, WorkspaceTab.Checks -> {
                                        item { EmptyPanel("Branch view supports Diff and History.") }
                                    }
                                }
                            }
                        }
                    }
                } ?: item {
                    EmptyPanel("Select a pull request from the stream.")
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pullStreamItems(
    pulls: List<PullRequest>,
    selectedPull: PullRequest?,
    state: LoadState,
    filter: PullFilter,
    onOpenPull: (PullRequest) -> Unit,
) {
    item {
        SectionTitle("Pulls", pulls.size.takeIf { it > 0 })
    }
    when (state) {
        LoadState.Loading -> item { LoadingRow("Loading pull stream") }
        is LoadState.Failed -> item { ErrorPanel(state.message) }
        LoadState.Idle -> {
            if (pulls.isEmpty()) {
                item { EmptyPanel("No ${filter.label.lowercase()} pull requests found.") }
            } else {
                items(pulls, key = { it.number }) { pull ->
                    PullCard(
                        pull = pull,
                        selected = selectedPull?.number == pull.number,
                        onClick = { onOpenPull(pull) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.branchItems(
    branches: List<Branch>,
    selectedBranch: Branch?,
    defaultBranch: String,
    state: LoadState,
    onSelectBranch: (Branch) -> Unit,
) {
    item { SectionTitle("Branches", branches.size.takeIf { it > 0 }) }
    when (state) {
        LoadState.Loading -> item { LoadingRow("Loading branch network") }
        is LoadState.Failed -> item { ErrorPanel(state.message) }
        LoadState.Idle -> {
            if (branches.isEmpty()) {
                item { EmptyPanel("No branches found.") }
            } else {
                items(branches, key = { it.name }) { branch ->
                    BranchCard(
                        branch = branch,
                        selected = branch.name == selectedBranch?.name,
                        isDefault = branch.name == defaultBranch,
                        onClick = { onSelectBranch(branch) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.codeItems(
    files: List<RepoTreeItem>,
    selectedPath: String?,
    treeState: LoadState,
    fileState: LoadState,
    fileContent: RepoFileContent?,
    canWrite: Boolean,
    branchName: String?,
    branchState: LoadState,
    newBranchName: String,
    pullTitle: String,
    pullBody: String,
    pullBaseBranch: String,
    pullState: LoadState,
    createMode: Boolean,
    newFilePath: String,
    newFileContent: String,
    createState: LoadState,
    conflictHint: String?,
    conflictBaseContent: RepoFileContent?,
    conflictBaseState: LoadState,
    conflictBaseRef: String?,
    editContent: String,
    commitMessage: String,
    writeState: LoadState,
    onNewBranchNameChange: (String) -> Unit,
    onCreateBranch: () -> Unit,
    onPullTitleChange: (String) -> Unit,
    onPullBodyChange: (String) -> Unit,
    onPullBaseBranchChange: (String) -> Unit,
    onOpenPull: () -> Unit,
    onCreateModeChange: (Boolean) -> Unit,
    onNewFilePathChange: (String) -> Unit,
    onNewFileContentChange: (String) -> Unit,
    onCreateFile: () -> Unit,
    onEditContentChange: (String) -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onCommitFile: () -> Unit,
    onSelectFile: (RepoTreeItem) -> Unit,
) {
    item { SectionTitle("Source Buffer") }
    when (fileState) {
        LoadState.Loading -> item { LoadingRow("Loading file") }
        is LoadState.Failed -> item { ErrorPanel(fileState.message) }
        LoadState.Idle -> {
            if (fileContent == null) {
                item { EmptyPanel("Select a repository file to inspect its content.") }
            } else {
                item {
                    CodeEditPanel(
                        canWrite = canWrite,
                        branchName = branchName,
                        branchState = branchState,
                        newBranchName = newBranchName,
                        pullTitle = pullTitle,
                        pullBody = pullBody,
                        pullBaseBranch = pullBaseBranch,
                        pullState = pullState,
                        createMode = createMode,
                        newFilePath = newFilePath,
                        newFileContent = newFileContent,
                        createState = createState,
                        conflictHint = conflictHint,
                        conflictBaseContent = conflictBaseContent,
                        conflictBaseState = conflictBaseState,
                        conflictBaseRef = conflictBaseRef,
                        content = editContent,
                        message = commitMessage,
                        state = writeState,
                        onNewBranchNameChange = onNewBranchNameChange,
                        onCreateBranch = onCreateBranch,
                        onPullTitleChange = onPullTitleChange,
                        onPullBodyChange = onPullBodyChange,
                        onPullBaseBranchChange = onPullBaseBranchChange,
                        onOpenPull = onOpenPull,
                        onCreateModeChange = onCreateModeChange,
                        onNewFilePathChange = onNewFilePathChange,
                        onNewFileContentChange = onNewFileContentChange,
                        onCreateFile = onCreateFile,
                        onContentChange = onEditContentChange,
                        onUseBaseContent = { conflictBaseContent?.content?.let(onEditContentChange) },
                        onUseHeadContent = { fileContent.content.let(onEditContentChange) },
                        onMessageChange = onCommitMessageChange,
                        onCommit = onCommitFile,
                    )
                }
                item { CodeBuffer(fileContent) }
            }
        }
    }

    item { SectionTitle("Repository Files", files.size.takeIf { it > 0 }) }
    when (treeState) {
        LoadState.Loading -> item { LoadingRow("Loading repository files") }
        is LoadState.Failed -> item { ErrorPanel(treeState.message) }
        LoadState.Idle -> {
            if (files.isEmpty()) {
                item { EmptyPanel("No repository files found.") }
            } else {
                items(files.take(80), key = { it.path }) { file ->
                    RepoFileCard(
                        file = file,
                        selected = file.path == selectedPath,
                        onClick = { onSelectFile(file) },
                    )
                }
                if (files.size > 80) {
                    item { EmptyPanel("Showing first 80 files on mobile.") }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.diffItems(
    details: PullDetails,
    selectedFileName: String?,
    highlightedPath: String?,
    highlightedLine: Int?,
    highlightedStartLine: Int?,
    highlightedSide: String?,
    highlightedStartSide: String?,
    onSelectedFileName: (String) -> Unit,
) {
    item { SectionTitle("Files", details.files.size) }
    if (details.files.isEmpty()) {
        item { EmptyPanel("No changed files loaded.") }
    } else {
        items(details.files, key = { "manifest-${it.filename}" }) { file ->
            FileManifestItem(
                file = file,
                selected = file.filename == selectedFileName,
                onClick = { onSelectedFileName(file.filename) },
            )
        }
        val selectedFile = details.files.firstOrNull { it.filename == selectedFileName } ?: details.files.first()
        item {
            SectionTitle("Source Buffer")
        }
        item {
            FileCard(
                file = selectedFile,
                highlightedLine = highlightedLine.takeIf { selectedFile.filename == highlightedPath },
                highlightedStartLine = highlightedStartLine.takeIf { selectedFile.filename == highlightedPath },
                highlightedSide = highlightedSide.takeIf { selectedFile.filename == highlightedPath },
                highlightedStartSide = highlightedStartSide.takeIf { selectedFile.filename == highlightedPath },
            )
        }
    }
}

@Composable
private fun CodeEditPanel(
    canWrite: Boolean,
    branchName: String?,
    branchState: LoadState,
    newBranchName: String,
    pullTitle: String,
    pullBody: String,
    pullBaseBranch: String,
    pullState: LoadState,
    createMode: Boolean,
    newFilePath: String,
    newFileContent: String,
    createState: LoadState,
    conflictHint: String?,
    conflictBaseContent: RepoFileContent?,
    conflictBaseState: LoadState,
    conflictBaseRef: String?,
    content: String,
    message: String,
    state: LoadState,
    onNewBranchNameChange: (String) -> Unit,
    onCreateBranch: () -> Unit,
    onPullTitleChange: (String) -> Unit,
    onPullBodyChange: (String) -> Unit,
    onPullBaseBranchChange: (String) -> Unit,
    onOpenPull: () -> Unit,
    onCreateModeChange: (Boolean) -> Unit,
    onNewFilePathChange: (String) -> Unit,
    onNewFileContentChange: (String) -> Unit,
    onCreateFile: () -> Unit,
    onContentChange: (String) -> Unit,
    onUseBaseContent: () -> Unit,
    onUseHeadContent: () -> Unit,
    onMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Code Edit",
                color = appStrong(0.82f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            conflictHint?.let {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = DiffRedSoft,
                    border = BorderStroke(1.dp, DiffRed.copy(alpha = 0.24f)),
                    contentColor = DiffRed,
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (canWrite) {
                Text(
                    branchName?.let { "Working branch: $it" } ?: "Select a branch from Network or create one before committing.",
                    color = appMuted(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RepoField(
                        value = newBranchName,
                        label = "New branch",
                        modifier = Modifier.weight(1f),
                        onValueChange = onNewBranchNameChange,
                    )
                    FilterButton(
                        text = if (branchState == LoadState.Loading) "Creating" else "Create",
                        selected = false,
                        onClick = {
                            if (branchState != LoadState.Loading) onCreateBranch()
                        },
                    )
                }
                when (branchState) {
                    is LoadState.Failed -> Text(
                        branchState.message,
                        color = DiffRed,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LoadState.Loading -> Text("Creating branch", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    LoadState.Idle -> Unit
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Edit File",
                        selected = !createMode,
                        onClick = { onCreateModeChange(false) },
                    )
                    FilterButton(
                        text = "New File",
                        selected = createMode,
                        onClick = { onCreateModeChange(true) },
                    )
                }
                if (createMode) {
                    RepoField(
                        value = newFilePath,
                        label = "New file path",
                        onValueChange = onNewFilePathChange,
                    )
                    OutlinedTextField(
                        value = newFileContent,
                        onValueChange = onNewFileContentChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 8,
                        maxLines = 14,
                        label = { Text("New file content") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = appStrong(),
                            fontFamily = FontFamily.Monospace,
                        ),
                        colors = codeTextFieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterButton(
                            text = if (createState == LoadState.Loading) "Creating" else "Create File",
                            selected = false,
                            onClick = {
                                if (createState != LoadState.Loading) onCreateFile()
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        when (createState) {
                            is LoadState.Failed -> Text(
                                createState.message,
                                color = DiffRed,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            LoadState.Loading -> Text("Creating file on branch", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                            LoadState.Idle -> Text(
                                branchName?.let { "Creates on $it" } ?: "Branch required",
                                color = appMuted(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                ConflictBasePanel(
                    baseContent = conflictBaseContent,
                    state = conflictBaseState,
                    baseRef = conflictBaseRef,
                    onUseBaseContent = onUseBaseContent,
                    onUseHeadContent = onUseHeadContent,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 14,
                    label = { Text("File content") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = appStrong(),
                        fontFamily = FontFamily.Monospace,
                    ),
                    colors = codeTextFieldColors(),
                )
                RepoField(
                    value = message,
                    label = "Commit message",
                    onValueChange = onMessageChange,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterButton(
                        text = if (state == LoadState.Loading) "Committing" else "Commit",
                        selected = false,
                        onClick = {
                            if (state != LoadState.Loading) onCommit()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    when (state) {
                        is LoadState.Failed -> Text(
                            state.message,
                            color = DiffRed,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoadState.Loading -> Text("Committing file", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                        LoadState.Idle -> Text(
                            branchName?.let { "Commits to $it" } ?: "Branch required",
                            color = appMuted(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                }
                Text(
                    "Open Pull Request",
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RepoField(
                        value = pullBaseBranch,
                        label = "Base",
                        modifier = Modifier.weight(0.38f),
                        onValueChange = onPullBaseBranchChange,
                    )
                    RepoField(
                        value = branchName.orEmpty(),
                        label = "Head",
                        modifier = Modifier.weight(0.62f),
                        onValueChange = {},
                    )
                }
                RepoField(
                    value = pullTitle,
                    label = "PR title",
                    onValueChange = onPullTitleChange,
                )
                OutlinedTextField(
                    value = pullBody,
                    onValueChange = onPullBodyChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("PR body") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = codeTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterButton(
                        text = if (pullState == LoadState.Loading) "Opening" else "Open PR",
                        selected = false,
                        onClick = {
                            if (pullState != LoadState.Loading) onOpenPull()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    when (pullState) {
                        is LoadState.Failed -> Text(
                            pullState.message,
                            color = DiffRed,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoadState.Loading -> Text("Opening pull request", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                        LoadState.Idle -> Text("Uses working branch", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text(
                    "Add a GitHub token in Account.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discussionItems(
    details: PullDetails,
    canWrite: Boolean,
    draft: String,
    writeState: LoadState,
    onDraftChange: (String) -> Unit,
    onSubmitDiscussion: () -> Unit,
    reviewDraft: String,
    reviewWriteState: LoadState,
    onReviewDraftChange: (String) -> Unit,
    onSubmitReview: (String) -> Unit,
    inlineDraft: String,
    inlineLine: String,
    inlineFileName: String?,
    inlineWriteState: LoadState,
    onInlineDraftChange: (String) -> Unit,
    onInlineLineChange: (String) -> Unit,
    onInlineFileChange: (String) -> Unit,
    onSubmitInline: () -> Unit,
    draftFixText: String,
    generatedDraftFix: String,
    draftFixState: LoadState,
    savedDraftsCount: Int,
    activeAiDraftId: String?,
    canGenerateDraftFix: Boolean,
    onClearDraftFix: () -> Unit,
    onDeleteDraftFix: () -> Unit,
    onGenerateDraftFix: () -> Unit,
    onUseDraftFixAsReview: () -> Unit,
    onOpenAnnotation: (PullComment) -> Unit,
    onDraftFix: (com.bniladridas.diff.model.PullComment) -> Unit,
) {
    val discussion = details.comments.filter { it.kind == CommentKind.Discussion }
    val annotations = details.comments.filter { it.kind == CommentKind.Review }

    item {
        DiscussionComposer(
            canWrite = canWrite,
            draft = draft,
            state = writeState,
            onDraftChange = onDraftChange,
            onSubmit = onSubmitDiscussion,
        )
    }
    item {
        ReviewComposer(
            canWrite = canWrite,
            draft = reviewDraft,
            state = reviewWriteState,
            onDraftChange = onReviewDraftChange,
            onSubmitReview = onSubmitReview,
        )
    }
    item {
        InlineCommentComposer(
            canWrite = canWrite,
            files = details.files.map { it.filename },
            selectedFile = inlineFileName ?: details.files.firstOrNull()?.filename,
            line = inlineLine,
            draft = inlineDraft,
            state = inlineWriteState,
            onFileChange = onInlineFileChange,
            onLineChange = onInlineLineChange,
            onDraftChange = onInlineDraftChange,
            onSubmit = onSubmitInline,
        )
    }
    if (draftFixText.isNotBlank()) {
        item {
            DraftFixPanel(
                draft = draftFixText,
                generated = generatedDraftFix,
                state = draftFixState,
                savedDraftsCount = savedDraftsCount,
                activeAiDraftId = activeAiDraftId,
                canGenerate = canGenerateDraftFix,
                onGenerate = onGenerateDraftFix,
                onUseAsReview = onUseDraftFixAsReview,
                onClear = onClearDraftFix,
                onDelete = onDeleteDraftFix,
            )
        }
    }

    item { SectionTitle("Discussion", discussion.size) }
    if (discussion.isEmpty()) {
        item { EmptyPanel("No comments yet.") }
    } else {
        items(discussion) { comment ->
            CommentCard(comment)
        }
    }

    item { SectionTitle("Annotations", annotations.size) }
    if (annotations.isEmpty()) {
        item { EmptyPanel("No inline annotations.") }
    } else {
        items(annotations) { comment ->
            CommentCard(
                comment = comment,
                onJumpToDiff = comment.path?.let { { onOpenAnnotation(comment) } },
                onDraftFix = comment.path?.let { { onDraftFix(comment) } },
            )
        }
    }
}

@Composable
private fun DraftFixPanel(
    draft: String,
    generated: String,
    state: LoadState,
    savedDraftsCount: Int,
    activeAiDraftId: String?,
    canGenerate: Boolean,
    onGenerate: () -> Unit,
    onUseAsReview: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Draft Fix",
                color = appStrong(0.82f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                draft,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .padding(12.dp),
                color = appStrong(0.78f),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterButton(
                    text = if (state == LoadState.Loading) "Generating" else "Generate",
                    selected = false,
                    onClick = {
                        if (canGenerate && state != LoadState.Loading) onGenerate()
                    },
                )
                FilterButton(
                    text = "Use as Review",
                    selected = false,
                    onClick = onUseAsReview,
                )
                FilterButton(
                    text = "Clear",
                    selected = false,
                    onClick = onClear,
                )
                if (activeAiDraftId != null) {
                    FilterButton(
                        text = "Delete Saved",
                        selected = false,
                        onClick = onDelete,
                    )
                }
            }
            when (state) {
                is LoadState.Failed -> Text(
                    state.message,
                    color = DiffRed,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LoadState.Loading -> Text("Generating Draft Fix with Gemini", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                LoadState.Idle -> Text(
                    when {
                        savedDraftsCount > 0 && canGenerate -> "$savedDraftsCount saved"
                        savedDraftsCount > 0 -> "$savedDraftsCount saved"
                        canGenerate -> "Gemini key is configured"
                        else -> "Add a Gemini key in Account"
                    },
                    color = appMuted(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (generated.isNotBlank()) {
                Text(
                    "Generated Fix",
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    generated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .padding(12.dp),
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 18,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "Uses your Gemini key.",
                color = appMuted(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ReviewComposer(
    canWrite: Boolean,
    draft: String,
    state: LoadState,
    onDraftChange: (String) -> Unit,
    onSubmitReview: (String) -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Review Decision",
                color = appStrong(0.82f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (canWrite) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Review summary") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = codeTextFieldColors(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterButton("Comment", false) {
                        if (state != LoadState.Loading) onSubmitReview("COMMENT")
                    }
                    FilterButton("Approve", false) {
                        if (state != LoadState.Loading) onSubmitReview("APPROVE")
                    }
                    FilterButton("Changes", false) {
                        if (state != LoadState.Loading) onSubmitReview("REQUEST_CHANGES")
                    }
                }
                when (state) {
                    is LoadState.Failed -> Text(
                        state.message,
                        color = DiffRed,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LoadState.Loading -> Text("Submitting review", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    LoadState.Idle -> Text("Uses your GitHub token", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    "Add a GitHub token in Account.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InlineCommentComposer(
    canWrite: Boolean,
    files: List<String>,
    selectedFile: String?,
    line: String,
    draft: String,
    state: LoadState,
    onFileChange: (String) -> Unit,
    onLineChange: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Inline Review Comment",
                color = appStrong(0.82f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (canWrite) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RepoField(
                        value = selectedFile.orEmpty(),
                        label = "File",
                        modifier = Modifier.weight(1f),
                        onValueChange = onFileChange,
                    )
                    RepoField(
                        value = line,
                        label = "Line",
                        modifier = Modifier.weight(0.35f),
                        onValueChange = onLineChange,
                    )
                }
                if (files.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        files.take(3).forEach { file ->
                            FilterButton(
                                text = file.substringAfterLast('/'),
                                selected = file == selectedFile,
                                onClick = { onFileChange(file) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Inline comment") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = codeTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterButton(
                        text = if (state == LoadState.Loading) "Posting" else "Post Inline",
                        selected = false,
                        onClick = {
                            if (state != LoadState.Loading) onSubmit()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    when (state) {
                        is LoadState.Failed -> Text(
                            state.message,
                            color = DiffRed,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoadState.Loading -> Text("Posting inline comment", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                        LoadState.Idle -> Text("Use a right-side line", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text(
                    "Add a GitHub token in Account.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DiscussionComposer(
    canWrite: Boolean,
    draft: String,
    state: LoadState,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Discussion Comment",
                color = appStrong(0.82f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (canWrite) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Add a comment") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = codeTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterButton(
                        text = if (state == LoadState.Loading) "Posting" else "Post",
                        selected = false,
                        onClick = {
                            if (state != LoadState.Loading) onSubmit()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    when (state) {
                        is LoadState.Failed -> Text(
                            state.message,
                            color = DiffRed,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoadState.Loading -> Text("Posting comment", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                        LoadState.Idle -> Text("Uses your GitHub token", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text(
                    "Add a GitHub token in Account.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.checksItems(
    details: PullDetails,
    state: LoadState,
    polling: Boolean,
    logs: Map<Long, String>,
    logStates: Map<Long, LoadState>,
    onRefresh: () -> Unit,
    onTogglePolling: () -> Unit,
    onLoadLog: (CheckRun) -> Unit,
    onClearLog: (CheckRun) -> Unit,
    onOpenAnnotation: (String) -> Unit,
) {
    val success = details.checks.count { it.conclusion == "success" }
    val failed = details.checks.count { it.conclusion in setOf("failure", "timed_out", "startup_failure") }
    val running = details.checks.count { it.conclusion == null && it.status != "completed" }
    val skipped = details.checks.count { it.conclusion == "skipped" }

    item { SectionTitle("Pipeline", details.checks.size) }
    item {
        ChecksRefreshPanel(
            state = state,
            polling = polling,
            running = running,
            onRefresh = onRefresh,
            onTogglePolling = onTogglePolling,
        )
    }
    if (details.checks.isNotEmpty()) {
        item {
            PipelineSummary(
                success = success,
                failed = failed,
                running = running,
                skipped = skipped,
            )
        }
    }
    if (details.checks.isEmpty()) {
        item { EmptyPanel("No check runs.") }
    } else {
        if (details.checks.none { it.summary != null || it.text != null }) {
            item { EmptyPanel("No inline output.") }
        }
        items(details.checks) { check ->
            CheckCard(
                check = check,
                log = logs[check.id],
                logState = logStates[check.id] ?: LoadState.Idle,
                onLoadLog = { onLoadLog(check) },
                onClearLog = { onClearLog(check) },
                onOpenAnnotation = onOpenAnnotation,
            )
        }
    }
}

@Composable
private fun ChecksRefreshPanel(
    state: LoadState,
    polling: Boolean,
    running: Int,
    onRefresh: () -> Unit,
    onTogglePolling: () -> Unit,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Check Refresh",
                        color = appStrong(0.82f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (running > 0) "$running running" else "No running checks",
                        color = appMuted(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                FilterButton(
                    text = if (state == LoadState.Loading) "Refreshing" else "Refresh",
                    selected = false,
                    onClick = {
                        if (state != LoadState.Loading) onRefresh()
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterButton(
                    text = if (polling) "Polling On" else "Polling Off",
                    selected = polling,
                    onClick = onTogglePolling,
                )
                Spacer(Modifier.weight(1f))
                when (state) {
                    is LoadState.Failed -> Text(
                        state.message,
                        color = DiffRed,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LoadState.Loading -> Text("Refreshing", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    LoadState.Idle -> Text("Checks only", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pullHistoryItems(details: PullDetails) {
    val entries = pullHistoryEntries(details)
    item { SectionTitle("History", entries.size) }
    if (entries.isEmpty()) {
        item { EmptyPanel("No history available.") }
    } else {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is HistoryEntry.Commit -> CommitCard(entry.commit)
                is HistoryEntry.Comment -> CommentCard(entry.comment)
                is HistoryEntry.Review -> ReviewCard(entry.review)
                is HistoryEntry.Timeline -> TimelineEventCard(entry.event)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.branchHistoryItems(details: PullDetails) {
    val commits = details.commits.sortedBy { it.date }
    item { SectionTitle("Commits", commits.size) }
    if (commits.isEmpty()) {
        item { EmptyPanel("No commits loaded.") }
    } else {
        items(commits, key = { it.sha }) { commit ->
            CommitCard(commit)
        }
    }
}

private fun historyEntryCount(details: PullDetails): Int =
    pullHistoryEntries(details).size

private fun pullHistoryEntries(details: PullDetails): List<HistoryEntry> {
    val entries = buildList {
        details.commits.forEach { add(HistoryEntry.Commit(it)) }
        details.comments.forEach { add(HistoryEntry.Comment(it)) }
        details.timeline.forEach { add(HistoryEntry.Timeline(it)) }
        if (details.timeline.none { it.kind == "reviewed" || it.kind == "review_dismissed" }) {
            details.reviews.forEach { add(HistoryEntry.Review(it)) }
        }
    }
    return entries
        .filter { it.date.isNotBlank() }
        .sortedBy { it.date }
}

@Composable
private fun PipelineSummary(
    success: Int,
    failed: Int,
    running: Int,
    skipped: Int,
) {
    FlowRow(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tag("$success passed", DiffGreen, DiffGreenSoft)
        if (failed > 0) Tag("$failed failed", DiffRed, DiffRedSoft)
        if (running > 0) Tag("$running running", BrandOrange, BrandOrangeSoft)
        if (skipped > 0) Tag("$skipped skipped", TextMuted, PanelRaised)
    }
}

@Composable
private fun AccountDialog(
    token: String,
    geminiKey: String,
    supabaseConfig: SupabaseConfig,
    login: String?,
    status: String?,
    syncState: LoadState,
    onDismiss: () -> Unit,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onSaveSupabaseConfig: (SupabaseConfig) -> Unit,
    onClearSupabaseConfig: () -> Unit,
    onStartSupabaseGitHubSignIn: (SupabaseConfig) -> Unit,
    onSignOutSupabase: (SupabaseConfig) -> Unit,
    onPullSupabasePreferences: (SupabaseConfig) -> Unit,
    onPushSupabasePreferences: (SupabaseConfig) -> Unit,
    onClearSavedState: () -> Unit,
    onVerifyToken: (String) -> Unit,
) {
    var draftToken by remember(token) { mutableStateOf(token) }
    var draftGeminiKey by remember(geminiKey) { mutableStateOf(geminiKey) }
    var draftSupabaseUrl by remember(supabaseConfig.url) { mutableStateOf(supabaseConfig.url) }
    var draftSupabaseAnonKey by remember(supabaseConfig.anonKey) { mutableStateOf(supabaseConfig.anonKey) }
    var draftSupabaseUserId by remember(supabaseConfig.userId) { mutableStateOf(supabaseConfig.userId) }
    var draftSupabaseAccessToken by remember(supabaseConfig.accessToken) { mutableStateOf(supabaseConfig.accessToken) }
    var draftSupabaseRefreshToken by remember(supabaseConfig.refreshToken) { mutableStateOf(supabaseConfig.refreshToken) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, appOutline()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Account",
                            color = appStrong(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            login?.let { "Connected as $it" } ?: "Use a GitHub token for authenticated reads.",
                            color = appMuted(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HeaderButton("Close", onDismiss)
                }
                OutlinedTextField(
                    value = draftToken,
                    onValueChange = { draftToken = it },
                    label = { Text("GitHub token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                Text(
                    "Fine-grained tokens should include repository read and write access for review, branch, and file edit actions.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
                status?.let {
                    Text(
                        it,
                        color = if (login != null) BrandOrange else appMuted(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Save",
                        selected = false,
                        onClick = { onSaveToken(draftToken) },
                    )
                    FilterButton(
                        text = "Verify",
                        selected = false,
                        onClick = {
                            onSaveToken(draftToken)
                            onVerifyToken(draftToken)
                        },
                    )
                    FilterButton(
                        text = "Clear",
                        selected = false,
                        onClick = {
                            draftToken = ""
                            onClearToken()
                        },
                    )
                }
                OutlinedTextField(
                    value = draftGeminiKey,
                    onValueChange = { draftGeminiKey = it },
                    label = { Text("Gemini API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Save Gemini",
                        selected = false,
                        onClick = { onSaveGeminiKey(draftGeminiKey) },
                    )
                    FilterButton(
                        text = "Clear Gemini",
                        selected = false,
                        onClick = {
                            draftGeminiKey = ""
                            onClearGeminiKey()
                        },
                    )
                }
                SectionTitle("Supabase Sync")
                Text(
                    "Matches the web user_preferences row for default repo, recent repos, and saved pulls. Add diff://auth/callback to Supabase redirect URLs.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = draftSupabaseUrl,
                    onValueChange = { draftSupabaseUrl = it },
                    label = { Text("Supabase URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                OutlinedTextField(
                    value = draftSupabaseAnonKey,
                    onValueChange = { draftSupabaseAnonKey = it },
                    label = { Text("Supabase anon key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                OutlinedTextField(
                    value = draftSupabaseUserId,
                    onValueChange = { draftSupabaseUserId = it },
                    label = { Text("Supabase user id") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                OutlinedTextField(
                    value = draftSupabaseAccessToken,
                    onValueChange = { draftSupabaseAccessToken = it },
                    label = { Text("Supabase access token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                OutlinedTextField(
                    value = draftSupabaseRefreshToken,
                    onValueChange = { draftSupabaseRefreshToken = it },
                    label = { Text("Supabase refresh token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = accountTextFieldColors(),
                )
                val canSync = draftSupabaseUrl.isNotBlank() &&
                    draftSupabaseAnonKey.isNotBlank() &&
                    draftSupabaseUserId.isNotBlank() &&
                    draftSupabaseAccessToken.isNotBlank()
                val canOAuth = draftSupabaseUrl.isNotBlank() && draftSupabaseAnonKey.isNotBlank()
                fun draftSupabaseConfig() = SupabaseConfig(
                    url = draftSupabaseUrl,
                    anonKey = draftSupabaseAnonKey,
                    userId = draftSupabaseUserId,
                    accessToken = draftSupabaseAccessToken,
                    refreshToken = draftSupabaseRefreshToken,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Save Sync",
                        selected = false,
                        onClick = { onSaveSupabaseConfig(draftSupabaseConfig()) },
                    )
                    FilterButton(
                        text = "GitHub OAuth",
                        selected = false,
                        enabled = canOAuth,
                        onClick = { onStartSupabaseGitHubSignIn(draftSupabaseConfig()) },
                    )
                    FilterButton(
                        text = "Sign Out",
                        selected = false,
                        enabled = draftSupabaseAccessToken.isNotBlank(),
                        onClick = {
                            onSignOutSupabase(draftSupabaseConfig())
                            draftToken = ""
                            draftSupabaseUserId = ""
                            draftSupabaseAccessToken = ""
                            draftSupabaseRefreshToken = ""
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Pull",
                        selected = false,
                        enabled = canSync && syncState != LoadState.Loading,
                        onClick = { onPullSupabasePreferences(draftSupabaseConfig()) },
                    )
                    FilterButton(
                        text = "Push",
                        selected = false,
                        enabled = canSync && syncState != LoadState.Loading,
                        onClick = { onPushSupabasePreferences(draftSupabaseConfig()) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Clear Sync",
                        selected = false,
                        onClick = {
                            draftSupabaseUrl = ""
                            draftSupabaseAnonKey = ""
                            draftSupabaseUserId = ""
                            draftSupabaseAccessToken = ""
                            draftSupabaseRefreshToken = ""
                            onClearSupabaseConfig()
                        },
                    )
                    Text(
                        when (syncState) {
                            LoadState.Loading -> "Syncing preferences"
                            is LoadState.Failed -> syncState.message
                            else -> if (supabaseConfig.isComplete) "Sync ready" else "Sync not configured"
                        },
                        color = appMuted(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterButton(
                        text = "Clear State",
                        selected = false,
                        onClick = onClearSavedState,
                    )
                    Text(
                        "Keeps token, clears saved pulls and repo history",
                        color = appMuted(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun appStrong(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
private fun appMuted(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

@Composable
private fun appOutline(strength: Float = 1f): Color {
    val outline = MaterialTheme.colorScheme.outline
    return outline.copy(alpha = outline.alpha * strength)
}

@Composable
private fun accountTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandOrange.copy(alpha = 0.44f),
    unfocusedBorderColor = appOutline(),
    focusedLabelColor = BrandOrange,
    unfocusedLabelColor = appMuted(),
    cursorColor = BrandOrange,
    focusedTextColor = appStrong(),
    unfocusedTextColor = appStrong(),
)

@Composable
private fun codeTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandOrange.copy(alpha = 0.44f),
    unfocusedBorderColor = appOutline(),
    focusedLabelColor = BrandOrange,
    unfocusedLabelColor = appMuted(),
    cursorColor = BrandOrange,
    focusedTextColor = appStrong(),
    unfocusedTextColor = appStrong(),
)

@Composable
private fun HeaderButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(end = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, appOutline()),
        contentColor = appMuted(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RepoPanel(
    owner: String,
    repo: String,
    defaultRepo: RepoRef,
    recentRepos: List<RepoRef>,
    savedPulls: List<SavedPull>,
    aiDrafts: List<AiDraft>,
    filter: PullFilter,
    streamMode: StreamMode,
    isBranchWorkspace: Boolean,
    onSelectRepo: (RepoRef) -> Unit,
    onSetDefaultRepo: () -> Unit,
    onResetDefaultRepo: () -> Unit,
    onOpenSavedPull: (SavedPull) -> Unit,
    onOpenAiDraft: (AiDraft) -> Unit,
    onFilterChange: (PullFilter) -> Unit,
    onStreamModeChange: (StreamMode) -> Unit,
    onLoad: () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var repoInput by remember(owner, repo) { mutableStateOf("$owner/$repo") }
    fun submitRepoInput() {
        val parts = repoInput.trim().split("/", limit = 2)
        val nextOwner = parts.getOrNull(0)?.trim().orEmpty()
        val nextRepo = parts.getOrNull(1)?.trim().orEmpty()
        if (nextOwner.isBlank() || nextRepo.isBlank()) return
        onSelectRepo(RepoRef(nextOwner, nextRepo))
        isEditing = false
    }

    BackHandler(enabled = isEditing) {
        repoInput = "$owner/$repo"
        isEditing = false
    }

    PanelCard {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "$owner/$repo",
                        color = appStrong(0.82f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (owner != defaultRepo.owner || repo != defaultRepo.repo) {
                    ControlButton("Pin", onClick = onSetDefaultRepo)
                }
                ControlButton(
                    text = if (isEditing) "Done" else "Switch",
                    onClick = {
                        if (isEditing) {
                            submitRepoInput()
                        } else {
                            repoInput = "$owner/$repo"
                            isEditing = true
                        }
                    },
                )
            }
            if (defaultRepo.owner != SystemOwner || defaultRepo.repo != SystemRepo) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilterButton(
                        text = "Default",
                        selected = false,
                        onClick = { onSelectRepo(defaultRepo) },
                    )
                    FilterButton(
                        text = "Clear",
                        selected = false,
                        onClick = onResetDefaultRepo,
                    )
                }
            }
            if (isEditing) {
                RepoField(
                    value = repoInput,
                    label = "owner/repo",
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { repoInput = it },
                )
            }
            if (recentRepos.isNotEmpty()) {
                CompactRepoSection(
                    title = "Recent Repos",
                    repos = recentRepos.filterNot { it.owner == owner && it.repo == repo }.take(4),
                    onSelectRepo = onSelectRepo,
                )
            }
            if (savedPulls.isNotEmpty()) {
                CompactSavedPullsSection(
                    savedPulls = savedPulls.take(4),
                    onOpenSavedPull = onOpenSavedPull,
                )
            }
            if (aiDrafts.isNotEmpty()) {
                CompactAiDraftsSection(
                    drafts = aiDrafts.take(4),
                    onOpenAiDraft = onOpenAiDraft,
                )
            }
            StreamModeSelector(
                streamMode = streamMode,
                isBranchWorkspace = isBranchWorkspace,
                onStreamModeChange = onStreamModeChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (streamMode == StreamMode.Pulls) {
                    PullFilter.entries.forEach { item ->
                        FilterButton(
                            text = item.label,
                            selected = filter == item,
                            onClick = { onFilterChange(item) },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                ControlButton(
                    text = "Refresh",
                    accent = true,
                    onClick = {
                        isEditing = false
                        onLoad()
                    },
                )
            }
        }
    }
}

@Composable
private fun StreamModeSelector(
    streamMode: StreamMode,
    isBranchWorkspace: Boolean,
    onStreamModeChange: (StreamMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, appOutline(0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            StreamMode.entries.forEach { item ->
                val selected = streamMode == item
                val label = if (item == StreamMode.Code && isBranchWorkspace) "Branch" else item.label
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStreamModeChange(item) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) BrandOrange.copy(alpha = 0.035f) else Color.Transparent,
                    contentColor = if (selected) BrandOrange.copy(alpha = 0.82f) else appMuted(),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (accent) BrandOrange.copy(alpha = 0.2f) else appOutline()),
        contentColor = if (accent) BrandOrange else appMuted(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WorkspaceActions(
    saved: Boolean,
    onToggleSaved: () -> Unit,
    canOpenBranch: Boolean,
    onOpenBranch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterButton(
            text = if (saved) "Unsave" else "Save",
            selected = saved,
            onClick = onToggleSaved,
        )
        FilterButton(
            text = "Edit Branch",
            selected = false,
            enabled = canOpenBranch,
            onClick = onOpenBranch,
        )
        Text(
            text = "Local saved state",
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BranchWorkspaceHeader(
    branch: Branch,
    baseBranch: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
        Text(
            "Branch View",
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
                )
                Text(
                    branch.name,
                    color = appStrong(0.88f),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Comparing head against $baseBranch",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BranchWorkspaceTabs(
    activeTab: WorkspaceTab,
    diffCount: Int,
    historyCount: Int,
    onSelect: (WorkspaceTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, appOutline(0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SegmentTab(
                text = "Diff${diffCount.takeIf { it > 0 }?.let { " $it" } ?: ""}",
                selected = activeTab == WorkspaceTab.Diff,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(WorkspaceTab.Diff) },
            )
            SegmentTab(
                text = "History${historyCount.takeIf { it > 0 }?.let { " $it" } ?: ""}",
                selected = activeTab == WorkspaceTab.History,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(WorkspaceTab.History) },
            )
        }
    }
}

@Composable
private fun SegmentTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) BrandOrange.copy(alpha = 0.055f) else Color.Transparent,
        contentColor = if (selected) BrandOrange else appMuted(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConflictBasePanel(
    baseContent: RepoFileContent?,
    state: LoadState,
    baseRef: String?,
    onUseBaseContent: () -> Unit,
    onUseHeadContent: () -> Unit,
) {
    if (baseRef == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Base version: $baseRef",
            color = appStrong(0.82f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (state) {
            LoadState.Loading -> Text("Loading base file", color = appMuted(), style = MaterialTheme.typography.labelSmall)
            is LoadState.Failed -> Text(
                state.message,
                color = DiffRed,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LoadState.Idle -> {
                val content = baseContent?.content
                if (content.isNullOrBlank()) {
                    Text("Base file unavailable for this path.", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(
                        content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                            .padding(9.dp),
                        color = appStrong(0.82f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterButton(
                text = "Use Base",
                selected = false,
                enabled = baseContent?.content?.isNotBlank() == true,
                onClick = onUseBaseContent,
            )
            FilterButton(
                text = "Use Head",
                selected = false,
                onClick = onUseHeadContent,
            )
        }
        Text(
            "Edit the head version below, then commit the resolved content.",
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PullManagementPanel(
    canWrite: Boolean,
    title: String,
    body: String,
    labels: String,
    state: LoadState,
    mergeMethod: String,
    mergeTitle: String,
    mergeMessage: String,
    mergeState: LoadState,
    updateBranchState: LoadState,
    deleteBranchState: LoadState,
    headBranch: String,
    merged: Boolean,
    mergeable: Boolean?,
    mergeableState: String?,
    pullOpen: Boolean,
    canResolveConflicts: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onLabelsChange: (String) -> Unit,
    onSave: () -> Unit,
    onMergeMethodChange: (String) -> Unit,
    onMergeTitleChange: (String) -> Unit,
    onMergeMessageChange: (String) -> Unit,
    onMerge: () -> Unit,
    onUpdateBranch: () -> Unit,
    onDeleteBranch: () -> Unit,
    onResolveConflicts: () -> Unit,
) {
    if (!expanded) {
        PullManagementSummary(
            canWrite = canWrite,
            merged = merged,
            mergeable = mergeable,
            mergeableState = mergeableState,
            pullOpen = pullOpen,
            onOpen = { onExpandedChange(true) },
        )
        return
    }

    PanelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pull Management",
                    modifier = Modifier.weight(1f),
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FilterButton("Hide", false) { onExpandedChange(false) }
            }
            MergeabilityStatus(
                merged = merged,
                mergeable = mergeable,
                mergeableState = mergeableState,
            )
            if (canWrite) {
                RepoField(
                    value = title,
                    label = "Title",
                    onValueChange = onTitleChange,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("Body") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = codeTextFieldColors(),
                )
                RepoField(
                    value = labels,
                    label = "Labels",
                    onValueChange = onLabelsChange,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterButton(
                        text = if (state == LoadState.Loading) "Saving" else "Save PR",
                        selected = false,
                        onClick = {
                            if (state != LoadState.Loading) onSave()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    when (state) {
                        is LoadState.Failed -> Text(
                            state.message,
                            color = DiffRed,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoadState.Loading -> Text("Updating pull request", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                        LoadState.Idle -> Text("Saves PR details", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "Merge",
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("merge", "squash", "rebase").forEach { method ->
                        FilterButton(
                            text = method.replaceFirstChar { it.uppercase() },
                            selected = mergeMethod == method,
                            onClick = { onMergeMethodChange(method) },
                        )
                    }
                }
                RepoField(
                    value = mergeTitle,
                    label = "Commit title",
                    onValueChange = onMergeTitleChange,
                )
                OutlinedTextField(
                    value = mergeMessage,
                    onValueChange = onMergeMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("Commit message") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
                    colors = codeTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterButton(
                        text = if (mergeState == LoadState.Loading) "Merging" else "Merge PR",
                        selected = false,
                        onClick = {
                            if (pullOpen && mergeState != LoadState.Loading) onMerge()
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    when (mergeState) {
                        is LoadState.Failed -> Text(
                            mergeState.message,
                            color = DiffRed,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoadState.Loading -> Text("Merging pull request", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                        LoadState.Idle -> Text(
                            if (pullOpen) "Uses selected merge method" else "Pull request is closed",
                            color = appMuted(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "Branch",
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Head: $headBranch",
                    color = appMuted(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pullOpen && mergeableState == "dirty") {
                        FilterButton(
                            text = "Resolve in Code",
                            selected = false,
                            enabled = canResolveConflicts,
                            onClick = onResolveConflicts,
                        )
                    }
                    FilterButton(
                        text = if (updateBranchState == LoadState.Loading) "Updating" else "Update Branch",
                        selected = false,
                        onClick = {
                            if (pullOpen && updateBranchState != LoadState.Loading) onUpdateBranch()
                        },
                    )
                    FilterButton(
                        text = if (deleteBranchState == LoadState.Loading) "Deleting" else "Delete Head",
                        selected = false,
                        onClick = {
                            if (merged && deleteBranchState != LoadState.Loading) onDeleteBranch()
                        },
                    )
                }
                when {
                    updateBranchState is LoadState.Failed -> Text(
                        updateBranchState.message,
                        color = DiffRed,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    deleteBranchState is LoadState.Failed -> Text(
                        deleteBranchState.message,
                        color = DiffRed,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    updateBranchState == LoadState.Loading -> Text("Updating branch", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    deleteBranchState == LoadState.Loading -> Text("Deleting branch", color = appMuted(), style = MaterialTheme.typography.labelSmall)
                    else -> Text(
                        if (merged) "Delete merged branch" else "Update from base",
                        color = appMuted(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                Text(
                    "Add a GitHub token in Account.",
                    color = appMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PullManagementSummary(
    canWrite: Boolean,
    merged: Boolean,
    mergeable: Boolean?,
    mergeableState: String?,
    pullOpen: Boolean,
    onOpen: () -> Unit,
) {
    val normalizedState = mergeableState?.replace("_", " ") ?: "checking"
    val label = when {
        merged -> "merged"
        mergeable == true -> normalizedState
        mergeable == false -> normalizedState
        else -> "checking"
    }
    val color = when {
        merged || mergeable == true && mergeableState in setOf("clean", "has_hooks", "unstable") -> DiffGreen
        mergeable == false || mergeableState in setOf("dirty", "blocked") -> DiffRed
        else -> appMuted()
    }
    val fill = when {
        merged || mergeable == true && mergeableState in setOf("clean", "has_hooks", "unstable") -> DiffGreenSoft
        mergeable == false || mergeableState in setOf("dirty", "blocked") -> DiffRedSoft
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    PanelCard {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "Manage PR",
                    color = appStrong(0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (canWrite) {
                        if (pullOpen) "Edit details, merge, or update branch" else "Closed pull request actions"
                    } else {
                        "Sign in to edit PR details"
                    },
                    color = appMuted(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Tag(label, color, fill)
            FilterButton("Open", false, onClick = onOpen)
        }
    }
}

@Composable
private fun MergeabilityStatus(
    merged: Boolean,
    mergeable: Boolean?,
    mergeableState: String?,
) {
    val normalizedState = mergeableState?.replace("_", " ") ?: "unknown"
    val label = when {
        merged -> "merged"
        mergeable == true -> normalizedState
        mergeable == false -> normalizedState
        else -> "checking"
    }
    val color = when {
        merged || mergeable == true && mergeableState in setOf("clean", "has_hooks", "unstable") -> DiffGreen
        mergeable == false || mergeableState in setOf("dirty", "blocked") -> DiffRed
        else -> TextMuted
    }
    val fill = when {
        merged || mergeable == true && mergeableState in setOf("clean", "has_hooks", "unstable") -> DiffGreenSoft
        mergeable == false || mergeableState in setOf("dirty", "blocked") -> DiffRedSoft
        else -> PanelRaised
    }
    val message = when (mergeableState) {
        "clean" -> "GitHub reports this pull request can merge cleanly."
        "dirty" -> "GitHub reports merge conflicts. Resolve in Code opens the PR branch editor."
        "behind" -> "The head branch is behind the base branch."
        "blocked" -> "GitHub reports required checks or reviews are blocking merge."
        "unstable" -> "The branch can merge, but checks may still be failing or pending."
        "draft" -> "Draft pull requests cannot be merged yet."
        null -> "GitHub has not reported mergeability yet."
        else -> "GitHub mergeability state: $normalizedState."
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mergeability",
                modifier = Modifier.weight(1f),
                color = appStrong(0.72f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Tag(label, color, fill)
        }
        Text(
            message,
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CompactRepoSection(
    title: String,
    repos: List<RepoRef>,
    onSelectRepo: (RepoRef) -> Unit,
) {
    if (repos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        repos.forEach { repoRef ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRepo(repoRef) },
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, appOutline()),
                contentColor = appMuted(),
            ) {
                Text(
                    "${repoRef.owner}/${repoRef.repo}",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactSavedPullsSection(
    savedPulls: List<SavedPull>,
    onOpenSavedPull: (SavedPull) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Saved Pulls",
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        savedPulls.forEach { savedPull ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSavedPull(savedPull) },
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, appOutline()),
                contentColor = appStrong(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            savedPull.title,
                            color = appStrong(0.82f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${savedPull.owner}/${savedPull.repo} #${savedPull.number}",
                            color = appMuted(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Tag(savedPull.state, BrandOrange, BrandOrangeSoft)
                }
            }
        }
    }
}

@Composable
private fun CompactAiDraftsSection(
    drafts: List<AiDraft>,
    onOpenAiDraft: (AiDraft) -> Unit,
) {
    if (drafts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Draft Fixes",
            color = appMuted(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        drafts.forEach { draft ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAiDraft(draft) },
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, appOutline()),
                contentColor = appMuted(),
            ) {
                Column(
                    modifier = Modifier.padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        draft.summary.ifBlank { draft.path },
                        color = appStrong(0.82f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${draft.owner}/${draft.repo} #${draft.pullNumber}",
                        color = appMuted(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = appStrong()),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandOrange.copy(alpha = 0.44f),
            unfocusedBorderColor = appOutline(),
            focusedLabelColor = BrandOrange,
            unfocusedLabelColor = appMuted(),
            cursorColor = BrandOrange,
            focusedTextColor = appStrong(),
            unfocusedTextColor = appStrong(),
        ),
    )
}

@Composable
private fun FilterButton(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) BrandOrange.copy(alpha = 0.035f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (selected) BrandOrange.copy(alpha = 0.12f) else appOutline(0.12f)),
        contentColor = when {
            !enabled -> appMuted(0.45f)
            selected -> BrandOrange.copy(alpha = 0.82f)
            else -> appMuted()
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
