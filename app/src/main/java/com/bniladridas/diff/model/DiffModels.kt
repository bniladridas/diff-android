package com.bniladridas.diff.model

enum class PullFilter(val apiValue: String, val label: String) {
    Open("open", "Open"),
    Closed("closed", "Closed"),
    All("all", "All"),
}

enum class WorkspaceTab(val label: String) {
    Diff("Diff"),
    Discussion("Discussion"),
    Checks("Checks"),
    History("History"),
}

enum class StreamMode(val label: String, val sectionLabel: String, val subtitle: String) {
    Pulls("Pulls", "Stream", "Pull requests"),
    Branches("Branches", "Network", "Branches"),
    Code("Code", "Explore", "Repository files"),
}

data class RepoRef(
    val owner: String,
    val repo: String,
)

data class SavedPull(
    val owner: String,
    val repo: String,
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String = "",
    val draft: Boolean = false,
    val savedAt: Long,
)

data class PullRequest(
    val number: Int,
    val title: String,
    val author: String,
    val body: String,
    val state: String,
    val draft: Boolean,
    val merged: Boolean,
    val mergeable: Boolean?,
    val mergeableState: String?,
    val createdAt: String,
    val base: String,
    val head: String,
    val headRepoFullName: String,
    val headSha: String,
    val htmlUrl: String,
    val labels: List<PullLabel>,
)

data class PullLabel(
    val name: String,
    val color: String,
)

data class ChangedFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String,
)

data class PullComment(
    val id: Long = 0L,
    val author: String,
    val body: String,
    val createdAt: String,
    val path: String?,
    val line: Int?,
    val startLine: Int?,
    val side: String?,
    val startSide: String?,
    val htmlUrl: String,
    val kind: CommentKind,
)

enum class CommentKind {
    Discussion,
    Review,
}

data class AiDraft(
    val id: String,
    val owner: String,
    val repo: String,
    val pullNumber: Int,
    val pullTitle: String,
    val branch: String,
    val path: String,
    val commentId: Long,
    val commentBody: String,
    val originalContent: String,
    val draftContent: String,
    val summary: String,
    val updatedAt: Long,
)

data class PullReview(
    val author: String,
    val body: String,
    val state: String,
    val submittedAt: String,
)

data class PullCommit(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
)

data class TimelineEvent(
    val id: String,
    val kind: String,
    val label: String,
    val actor: String,
    val date: String,
    val body: String?,
    val commitSha: String?,
)

data class CheckRun(
    val id: Long,
    val jobId: Long?,
    val name: String,
    val status: String,
    val conclusion: String?,
    val url: String,
    val startedAt: String?,
    val completedAt: String?,
    val summary: String?,
    val text: String?,
    val annotations: List<CheckAnnotation>,
)

data class CheckAnnotation(
    val path: String,
    val startLine: Int?,
    val endLine: Int?,
    val level: String,
    val title: String?,
    val message: String,
)

data class PullDetails(
    val files: List<ChangedFile>,
    val comments: List<PullComment>,
    val reviews: List<PullReview>,
    val commits: List<PullCommit>,
    val timeline: List<TimelineEvent>,
    val checks: List<CheckRun>,
)

data class Branch(
    val name: String,
    val sha: String,
    val protected: Boolean,
)

data class RepoTreeItem(
    val path: String,
    val type: String,
    val sha: String,
    val size: Int?,
)

data class RepoFileContent(
    val path: String,
    val content: String,
    val sha: String,
)

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Failed(val message: String) : LoadState
}
