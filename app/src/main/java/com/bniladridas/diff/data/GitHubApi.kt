package com.bniladridas.diff.data

import com.bniladridas.diff.model.ChangedFile
import com.bniladridas.diff.model.Branch
import com.bniladridas.diff.model.CheckAnnotation
import com.bniladridas.diff.model.CheckRun
import com.bniladridas.diff.model.CommentKind
import com.bniladridas.diff.model.PullComment
import com.bniladridas.diff.model.PullCommit
import com.bniladridas.diff.model.PullDetails
import com.bniladridas.diff.model.PullFilter
import com.bniladridas.diff.model.PullLabel
import com.bniladridas.diff.model.PullRequest
import com.bniladridas.diff.model.PullReview
import com.bniladridas.diff.model.RepoFileContent
import com.bniladridas.diff.model.RepoTreeItem
import com.bniladridas.diff.model.TimelineEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Base64

object GitHubApi {
    private var authToken: String = ""

    fun setAuthToken(token: String) {
        authToken = token.trim()
    }

    suspend fun fetchViewer(): Result<String> =
        runCatching {
            val body = get("https://api.github.com/user")
            JSONObject(body).optString("login").ifBlank { "GitHub" }
        }

    suspend fun fetchPulls(owner: String, repo: String, filter: PullFilter): Result<List<PullRequest>> =
        runCatching {
            val url = "https://api.github.com/repos/$owner/$repo/pulls?state=${filter.apiValue}&per_page=30"
            val array = JSONArray(get(url))
            List(array.length()) { index ->
                array.getJSONObject(index).toPullRequest()
            }.sortedByDescending { it.number }
        }

    suspend fun fetchPullRequest(owner: String, repo: String, number: Int): Result<PullRequest> =
        runCatching {
            JSONObject(get("https://api.github.com/repos/$owner/$repo/pulls/$number")).toPullRequest()
        }

    suspend fun fetchBranches(owner: String, repo: String): Result<List<Branch>> =
        runCatching {
            val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/branches?per_page=100"))
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                Branch(
                    name = item.optString("name"),
                    sha = item.getJSONObject("commit").optString("sha"),
                    protected = item.optBoolean("protected"),
                )
            }
        }

    suspend fun fetchDefaultBranch(owner: String, repo: String): Result<String> =
        runCatching {
            JSONObject(get("https://api.github.com/repos/$owner/$repo"))
                .optString("default_branch")
                .ifBlank { "HEAD" }
        }

    suspend fun fetchBranchComparison(owner: String, repo: String, base: String, head: String): Result<PullDetails> =
        runCatching {
            if (base.isBlank()) error("Base branch is required.")
            if (head.isBlank()) error("Head branch is required.")
            if (base == head) {
                return@runCatching PullDetails(
                    files = emptyList(),
                    comments = emptyList(),
                    reviews = emptyList(),
                    commits = emptyList(),
                    timeline = emptyList(),
                    checks = emptyList(),
                )
            }
            val encodedBase = encodeUrlPathValue(base)
            val encodedHead = encodeUrlPathValue(head)
            val body = get("https://api.github.com/repos/$owner/$repo/compare/$encodedBase...$encodedHead")
            val compare = JSONObject(body)
            val files = compare.optJSONArray("files") ?: JSONArray()
            val commits = compare.optJSONArray("commits") ?: JSONArray()
            PullDetails(
                files = List(files.length()) { index -> files.getJSONObject(index).toChangedFile() },
                comments = emptyList(),
                reviews = emptyList(),
                commits = List(commits.length()) { index -> commits.getJSONObject(index).toPullCommit() }
                    .sortedByDescending { it.date },
                timeline = emptyList(),
                checks = emptyList(),
            )
        }

    suspend fun fetchRepoTree(owner: String, repo: String, ref: String = "HEAD"): Result<List<RepoTreeItem>> =
        runCatching {
            val encodedRef = encodeUrlPathValue(ref)
            val body = get("https://api.github.com/repos/$owner/$repo/git/trees/$encodedRef?recursive=1")
            val array = JSONObject(body).optJSONArray("tree") ?: JSONArray()
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                RepoTreeItem(
                    path = item.optString("path"),
                    type = item.optString("type"),
                    sha = item.optString("sha"),
                    size = item.optIntOrNull("size"),
                )
            }
                .filter { it.type == "blob" }
                .sortedBy { it.path }
        }

    suspend fun fetchRepoFile(owner: String, repo: String, path: String, ref: String = "HEAD"): Result<RepoFileContent> =
        runCatching {
            val encodedPath = encodeUrlPath(path)
            val encodedRef = encodeUrlQueryValue(ref)
            val body = get("https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=$encodedRef")
            val item = JSONObject(body)
            val encoding = item.optString("encoding")
            val rawContent = item.optString("content")
            val content = if (encoding == "base64") {
                String(Base64.getMimeDecoder().decode(rawContent), Charsets.UTF_8)
            } else {
                rawContent
            }
            RepoFileContent(
                path = item.optString("path", path),
                content = content,
                sha = item.optString("sha"),
            )
        }

    suspend fun commitRepoFile(
        owner: String,
        repo: String,
        file: RepoFileContent,
        branch: String?,
        content: String,
        message: String,
    ): Result<RepoFileContent> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before committing files.")
            if (message.isBlank()) error("Commit message is required.")
            if (file.sha.isBlank()) error("File sha is required before committing.")
            val encodedPath = encodeUrlPath(file.path)
            val payload = JSONObject()
                .put("message", message.trim())
                .put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
                .put("sha", file.sha)
            if (!branch.isNullOrBlank() && branch != "HEAD") {
                payload.put("branch", branch)
            }
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath",
                method = "PUT",
                body = payload.toString(),
            )
            val contentJson = JSONObject(response).getJSONObject("content")
            RepoFileContent(
                path = contentJson.optString("path", file.path),
                content = content,
                sha = contentJson.optString("sha"),
            )
        }

    suspend fun createRepoFile(
        owner: String,
        repo: String,
        path: String,
        branch: String?,
        content: String,
        message: String,
    ): Result<RepoFileContent> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before creating files.")
            val cleanPath = path.trim().trim('/')
            if (cleanPath.isBlank()) error("File path is required.")
            if (message.isBlank()) error("Commit message is required.")
            val encodedPath = encodeUrlPath(cleanPath)
            val payload = JSONObject()
                .put("message", message.trim())
                .put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            if (!branch.isNullOrBlank() && branch != "HEAD") {
                payload.put("branch", branch)
            }
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath",
                method = "PUT",
                body = payload.toString(),
            )
            val contentJson = JSONObject(response).getJSONObject("content")
            RepoFileContent(
                path = contentJson.optString("path", cleanPath),
                content = content,
                sha = contentJson.optString("sha"),
            )
        }

    suspend fun createBranch(owner: String, repo: String, name: String, sourceSha: String): Result<Branch> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before creating branches.")
            val cleanName = name.trim()
            if (cleanName.isBlank()) error("Branch name is required.")
            if (cleanName.contains(" ")) error("Branch names cannot contain spaces.")
            if (sourceSha.isBlank()) error("Choose a source branch before creating a branch.")
            request(
                url = "https://api.github.com/repos/$owner/$repo/git/refs",
                method = "POST",
                body = JSONObject()
                    .put("ref", "refs/heads/$cleanName")
                    .put("sha", sourceSha)
                    .toString(),
            )
            Branch(name = cleanName, sha = sourceSha, protected = false)
        }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
    ): Result<PullRequest> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before opening pull requests.")
            if (title.isBlank()) error("Pull request title is required.")
            if (head.isBlank()) error("Choose a source branch before opening a pull request.")
            if (base.isBlank()) error("Base branch is required.")
            if (head == base) error("Source and base branches must be different.")
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/pulls",
                method = "POST",
                body = JSONObject()
                    .put("title", title.trim())
                    .put("body", body.trim())
                    .put("head", head)
                    .put("base", base.trim())
                    .toString(),
            )
            JSONObject(response).toPullRequest()
        }

    suspend fun updatePullRequest(
        owner: String,
        repo: String,
        number: Int,
        title: String,
        body: String,
    ): Result<PullRequest> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before updating pull requests.")
            if (title.isBlank()) error("Pull request title is required.")
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/pulls/$number",
                method = "PATCH",
                body = JSONObject()
                    .put("title", title.trim())
                    .put("body", body.trim())
                    .toString(),
            )
            JSONObject(response).toPullRequest()
        }

    suspend fun updateIssueLabels(owner: String, repo: String, number: Int, labels: List<String>): Result<List<PullLabel>> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before updating labels.")
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/issues/$number/labels",
                method = "PUT",
                body = JSONObject()
                    .put("labels", JSONArray(labels.map { it.trim() }.filter { it.isNotBlank() }))
                    .toString(),
            )
            val array = JSONArray(response)
            List(array.length()) { index ->
                array.getJSONObject(index).toPullLabel()
            }
        }

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: String,
        title: String,
        message: String,
    ): Result<Unit> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before merging pull requests.")
            val cleanMethod = method.lowercase()
            if (cleanMethod !in setOf("merge", "squash", "rebase")) error("Choose merge, squash, or rebase.")
            val payload = JSONObject().put("merge_method", cleanMethod)
            if (title.isNotBlank()) payload.put("commit_title", title.trim())
            if (message.isNotBlank()) payload.put("commit_message", message.trim())
            request(
                url = "https://api.github.com/repos/$owner/$repo/pulls/$number/merge",
                method = "PUT",
                body = payload.toString(),
            )
        }

    suspend fun updatePullBranch(owner: String, repo: String, number: Int): Result<Unit> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before updating branches.")
            request(
                url = "https://api.github.com/repos/$owner/$repo/pulls/$number/update-branch",
                method = "PUT",
                body = JSONObject().toString(),
            )
        }

    suspend fun deleteBranch(owner: String, repo: String, branch: String): Result<Unit> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before deleting branches.")
            val cleanBranch = branch.trim()
            if (cleanBranch.isBlank()) error("Branch name is required.")
            if (cleanBranch == "main" || cleanBranch == "master") error("Refusing to delete the default branch name.")
            val encodedBranch = encodeUrlPathValue(cleanBranch)
            request(
                url = "https://api.github.com/repos/$owner/$repo/git/refs/heads/$encodedBranch",
                method = "DELETE",
                body = null,
            )
        }

    suspend fun fetchPullDetails(owner: String, repo: String, pull: PullRequest): Result<PullDetails> =
        runCatching {
            coroutineScope {
                val files = async { fetchFiles(owner, repo, pull.number) }
                val issueComments = async { fetchIssueComments(owner, repo, pull.number) }
                val reviewComments = async { fetchReviewComments(owner, repo, pull.number) }
                val reviews = async { fetchReviews(owner, repo, pull.number) }
                val commits = async { fetchCommits(owner, repo, pull.number) }
                val timeline = async { runCatching { fetchTimeline(owner, repo, pull.number) }.getOrElse { emptyList() } }

                PullDetails(
                    files = files.await(),
                    comments = (issueComments.await() + reviewComments.await()).sortedByDescending { it.createdAt },
                    reviews = reviews.await().sortedByDescending { it.submittedAt },
                    commits = commits.await().sortedByDescending { it.date },
                    timeline = timeline.await().sortedByDescending { it.date },
                    checks = emptyList(),
                )
            }
        }

    suspend fun fetchPullChecks(owner: String, repo: String, pull: PullRequest): Result<List<CheckRun>> =
        runCatching {
            fetchChecks(owner, repo, pull.headSha)
        }

    suspend fun fetchCheckLog(owner: String, repo: String, check: CheckRun): Result<String> =
        runCatching {
            val jobId = check.jobId ?: error("This check does not expose a GitHub Actions job log.")
            get("https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs")
                .take(24_000)
        }

    suspend fun postIssueComment(owner: String, repo: String, number: Int, body: String): Result<PullComment> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before posting comments.")
            if (body.isBlank()) error("Comment body is required.")
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/issues/$number/comments",
                method = "POST",
                body = JSONObject().put("body", body.trim()).toString(),
            )
            JSONObject(response).toIssueComment()
        }

    suspend fun submitReview(
        owner: String,
        repo: String,
        number: Int,
        event: String,
        body: String,
    ): Result<PullReview> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before submitting reviews.")
            if (event == "REQUEST_CHANGES" && body.isBlank()) error("Add review guidance before requesting changes.")
            val payload = JSONObject()
                .put("event", event)
                .put("body", body.trim())
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/pulls/$number/reviews",
                method = "POST",
                body = payload.toString(),
            )
            JSONObject(response).toPullReview()
        }

    suspend fun postInlineReviewComment(
        owner: String,
        repo: String,
        pull: PullRequest,
        path: String,
        line: Int,
        body: String,
    ): Result<PullComment> =
        runCatching {
            if (authToken.isBlank()) error("Add a GitHub token before posting inline comments.")
            if (path.isBlank()) error("Choose a file for the inline comment.")
            if (line <= 0) error("Enter a valid diff line.")
            if (body.isBlank()) error("Comment body is required.")
            val payload = JSONObject()
                .put("body", body.trim())
                .put("commit_id", pull.headSha)
                .put("path", path)
                .put("line", line)
                .put("side", "RIGHT")
            val response = request(
                url = "https://api.github.com/repos/$owner/$repo/pulls/${pull.number}/comments",
                method = "POST",
                body = payload.toString(),
            )
            JSONObject(response).toReviewComment()
        }

    private suspend fun fetchFiles(owner: String, repo: String, number: Int): List<ChangedFile> {
        val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/pulls/$number/files?per_page=100"))
        return List(array.length()) { index ->
            array.getJSONObject(index).toChangedFile()
        }
    }

    private suspend fun fetchIssueComments(owner: String, repo: String, number: Int): List<PullComment> {
        val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/issues/$number/comments?per_page=100"))
        return List(array.length()) { index ->
            array.getJSONObject(index).toIssueComment()
        }
    }

    private suspend fun fetchReviewComments(owner: String, repo: String, number: Int): List<PullComment> {
        val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/pulls/$number/comments?per_page=100"))
        return List(array.length()) { index ->
            array.getJSONObject(index).toReviewComment()
        }
    }

    private suspend fun fetchReviews(owner: String, repo: String, number: Int): List<PullReview> {
        val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/pulls/$number/reviews?per_page=100"))
        return List(array.length()) { index ->
            array.getJSONObject(index).toPullReview()
        }
    }

    private suspend fun fetchCommits(owner: String, repo: String, number: Int): List<PullCommit> {
        val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/pulls/$number/commits?per_page=100"))
        return List(array.length()) { index ->
            array.getJSONObject(index).toPullCommit()
        }
    }

    private suspend fun fetchChecks(owner: String, repo: String, ref: String): List<CheckRun> {
        if (ref.isBlank()) return emptyList()
        val body = get("https://api.github.com/repos/$owner/$repo/commits/$ref/check-runs?per_page=50")
        val array = JSONObject(body).optJSONArray("check_runs") ?: JSONArray()
        val checks = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val id = item.optLong("id")
            val output = item.optJSONObject("output")
            val annotationsCount = output?.optInt("annotations_count") ?: 0
            CheckWithAnnotationCount(
                check = CheckRun(
                    id = id,
                    jobId = item.optCleanString("details_url")?.let { extractJobId(it) },
                    name = item.optString("name"),
                    status = item.optString("status"),
                    conclusion = item.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
                    url = item.optString("html_url"),
                    startedAt = item.optCleanString("started_at"),
                    completedAt = item.optCleanString("completed_at"),
                    summary = output?.optCleanString("summary"),
                    text = output?.optCleanString("text"),
                    annotations = emptyList(),
                ),
                annotationsCount = annotationsCount,
            )
        }
        return coroutineScope {
            checks.map { item ->
                async {
                    if (item.annotationsCount <= 0) {
                        item.check
                    } else {
                        item.check.copy(annotations = fetchCheckAnnotations(owner, repo, item.check.id))
                    }
                }
            }.map { it.await() }
        }
    }

    private fun extractJobId(detailsUrl: String): Long? =
        """/job/(\d+)""".toRegex()
            .find(detailsUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private suspend fun fetchCheckAnnotations(owner: String, repo: String, checkRunId: Long): List<CheckAnnotation> {
        if (checkRunId <= 0L) return emptyList()
        val body = get("https://api.github.com/repos/$owner/$repo/check-runs/$checkRunId/annotations?per_page=50")
        val array = JSONArray(body)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            CheckAnnotation(
                path = item.optString("path"),
                startLine = item.optIntOrNull("start_line"),
                endLine = item.optIntOrNull("end_line"),
                level = item.optString("annotation_level"),
                title = item.optCleanString("title"),
                message = item.optString("message"),
            )
        }
    }

    private suspend fun fetchTimeline(owner: String, repo: String, number: Int): List<TimelineEvent> {
        val array = JSONArray(get("https://api.github.com/repos/$owner/$repo/issues/$number/timeline?per_page=100"))
        return List(array.length()) { index -> array.getJSONObject(index) }
            .filter { item ->
                val event = item.optString("event")
                when {
                    event == "commented" -> false
                    event == "committed" -> false
                    event == "reviewed" &&
                        item.optString("state").equals("COMMENTED", ignoreCase = true) &&
                        item.optCleanString("body") == null -> false
                    else -> true
                }
            }
            .mapIndexed { index, item ->
                val event = item.optString("event")
                val actor = item.optJSONObject("actor")?.optString("login")
                    ?: item.optJSONObject("user")?.optString("login")
                    ?: "github"
                TimelineEvent(
                    id = item.optString("id", "$event-$index"),
                    kind = event.ifBlank { item.optString("state", "event") },
                    label = timelineLabel(item),
                    actor = actor,
                    date = item.optString("created_at", item.optString("submitted_at")),
                    body = item.optCleanString("body"),
                    commitSha = item.optCleanString("commit_id"),
                )
            }.filter { it.date.isNotBlank() }
    }

    private fun JSONObject.toPullRequest(): PullRequest {
        val labelsJson = optJSONArray("labels") ?: JSONArray()
        val labels = List(labelsJson.length()) { index ->
            labelsJson.getJSONObject(index).toPullLabel()
        }
        return PullRequest(
            number = getInt("number"),
            title = optString("title"),
            author = getJSONObject("user").optString("login"),
            body = optString("body"),
            state = optString("state"),
            draft = optBoolean("draft"),
            merged = optBoolean("merged"),
            mergeable = optBooleanOrNull("mergeable"),
            mergeableState = optCleanString("mergeable_state"),
            createdAt = optString("created_at"),
            base = getJSONObject("base").optString("ref"),
            head = getJSONObject("head").optString("ref"),
            headRepoFullName = getJSONObject("head").optJSONObject("repo")?.optString("full_name").orEmpty(),
            headSha = getJSONObject("head").optString("sha"),
            htmlUrl = optString("html_url"),
            labels = labels,
        )
    }

    private fun JSONObject.toPullLabel(): PullLabel =
        PullLabel(
            name = optString("name"),
            color = optString("color"),
        )

    private fun JSONObject.toChangedFile(): ChangedFile =
        ChangedFile(
            filename = optString("filename"),
            status = optString("status"),
            additions = optInt("additions"),
            deletions = optInt("deletions"),
            changes = optInt("changes"),
            patch = optString("patch"),
        )

    private fun JSONObject.toPullCommit(): PullCommit {
        val commit = getJSONObject("commit")
        val author = commit.optJSONObject("author")
        return PullCommit(
            sha = optString("sha"),
            message = commit.optString("message").lineSequence().firstOrNull().orEmpty(),
            author = author?.optString("name").orEmpty(),
            date = author?.optString("date").orEmpty(),
        )
    }

    private fun JSONObject.toIssueComment(): PullComment =
        PullComment(
            id = optLong("id"),
            author = getJSONObject("user").optString("login"),
            body = optString("body"),
            createdAt = optString("created_at"),
            path = null,
            line = null,
            startLine = null,
            side = null,
            startSide = null,
            htmlUrl = optString("html_url"),
            kind = CommentKind.Discussion,
        )

    private fun JSONObject.toPullReview(): PullReview =
        PullReview(
            author = getJSONObject("user").optString("login"),
            body = optString("body"),
            state = optString("state"),
            submittedAt = optString("submitted_at"),
        )

    private fun JSONObject.toReviewComment(): PullComment {
        val line = optIntOrNull("line")
        val originalLine = optIntOrNull("original_line")
        val side = optString("side").takeIf { it.isNotBlank() }
            ?: if (line == null && originalLine != null) "LEFT" else null
        return PullComment(
            id = optLong("id"),
            author = getJSONObject("user").optString("login"),
            body = optString("body"),
            createdAt = optString("created_at"),
            path = optString("path").takeIf { it.isNotBlank() },
            line = line ?: originalLine,
            startLine = optIntOrNull("start_line") ?: optIntOrNull("original_start_line"),
            side = side,
            startSide = optString("start_side").takeIf { it.isNotBlank() },
            htmlUrl = optString("html_url"),
            kind = CommentKind.Review,
        )
    }

    private suspend fun get(url: String): String =
        request(url = url, method = "GET", body = null)

    private suspend fun request(url: String, method: String, body: String?): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DIFF-Android")
            if (authToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $authToken")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                error(
                    GitHubErrors.format(
                        code = connection.responseCode,
                        body = body,
                        resetEpochSeconds = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull(),
                    ),
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}

private data class CheckWithAnnotationCount(
    val check: CheckRun,
    val annotationsCount: Int,
)

private fun encodeUrlPath(path: String): String =
    path.split("/").joinToString("/") { encodeUrlPathValue(it) }

private fun encodeUrlPathValue(value: String): String =
    encodeUrlQueryValue(value).replace("+", "%20")

private fun encodeUrlQueryValue(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())

private fun timelineLabel(item: JSONObject): String {
    val event = item.optString("event")
    return when (event) {
        "closed" -> "Closed"
        "reopened" -> "Reopened"
        "merged" -> "Merged"
        "reviewed" -> when (item.optString("state").uppercase()) {
            "APPROVED" -> "Review: approved"
            "CHANGES_REQUESTED" -> "Review: changes requested"
            "DISMISSED" -> "Review: dismissed"
            else -> "Review: commented"
        }
        "review_dismissed" -> "Review dismissed"
        "review_requested" -> "Requested review"
        "review_request_removed" -> "Removed review request"
        "assigned" -> "Assigned"
        "unassigned" -> "Unassigned"
        "labeled" -> "Label added"
        "unlabeled" -> "Label removed"
        "renamed" -> "Title changed"
        "edited" -> "Details updated"
        "head_ref_force_pushed" -> "Force pushed"
        "head_ref_deleted" -> "Deleted branch"
        "head_ref_restored" -> "Restored branch"
        "ready_for_review" -> "Ready for review"
        "convert_to_draft" -> "Converted to draft"
        "converted_to_draft" -> "Converted to draft"
        "cross-referenced" -> "Linked issue"
        "commented" -> "Commented"
        "committed" -> "Committed"
        else -> event.replace("_", " ").replaceFirstChar { it.uppercase() }.ifBlank { "Timeline event" }
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}

private fun JSONObject.optBooleanOrNull(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return optBoolean(name)
}

private fun JSONObject.optCleanString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() && it != "null" }
}
