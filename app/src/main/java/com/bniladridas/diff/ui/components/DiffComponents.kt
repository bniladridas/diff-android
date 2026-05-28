package com.bniladridas.diff.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bniladridas.diff.R
import com.bniladridas.diff.model.ChangedFile
import com.bniladridas.diff.model.Branch
import com.bniladridas.diff.model.CheckAnnotation
import com.bniladridas.diff.model.CheckRun
import com.bniladridas.diff.model.CommentKind
import com.bniladridas.diff.model.LoadState
import com.bniladridas.diff.model.PullComment
import com.bniladridas.diff.model.PullCommit
import com.bniladridas.diff.model.PullRequest
import com.bniladridas.diff.model.PullReview
import com.bniladridas.diff.model.RepoFileContent
import com.bniladridas.diff.model.RepoTreeItem
import com.bniladridas.diff.model.TimelineEvent
import com.bniladridas.diff.model.WorkspaceTab
import com.bniladridas.diff.ui.formatDate
import com.bniladridas.diff.ui.shortSha
import com.bniladridas.diff.ui.theme.BrandOrange
import com.bniladridas.diff.ui.theme.BrandOrangeSoft
import com.bniladridas.diff.ui.theme.DiffBlue
import com.bniladridas.diff.ui.theme.DiffBlueSoft
import com.bniladridas.diff.ui.theme.DiffGreen
import com.bniladridas.diff.ui.theme.DiffGreenSoft
import com.bniladridas.diff.ui.theme.DiffHighlight
import com.bniladridas.diff.ui.theme.DiffHighlightText
import com.bniladridas.diff.ui.theme.DiffLine
import com.bniladridas.diff.ui.theme.DiffRed
import com.bniladridas.diff.ui.theme.DiffRedSoft
import com.bniladridas.diff.ui.theme.PanelRaised
import com.bniladridas.diff.ui.theme.TextMuted

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Card(
        modifier = clickModifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) BrandOrange.copy(alpha = 0.22f) else outlineText(0.7f)),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) BrandOrange.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        ),
    ) {
        content()
    }
}

@Composable
private fun strongText(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
private fun bodyText(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
private fun mutedText(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

@Composable
private fun outlineText(strength: Float = 1f): Color {
    val outline = MaterialTheme.colorScheme.outline
    return outline.copy(alpha = outline.alpha * strength)
}

@Composable
private fun codeSurface(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)

@Composable
private fun codeText(alpha: Float = 1f): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

@Composable
private fun NeutralTag(text: String) {
    Tag(text, mutedText(0.72f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
}

@Composable
fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFF111418), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_diff_mark),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            "DIFF",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
fun PullCard(
    pull: PullRequest,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PanelCard(selected = selected, onClick = onClick) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(pull.state, pull.merged)
                Spacer(Modifier.width(7.dp))
                Text(
                    "#${pull.number}",
                    color = BrandOrange.copy(alpha = 0.78f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(7.dp))
                if (pull.draft) NeutralTag("Draft")
                Spacer(Modifier.weight(1f))
                Tag(if (pull.merged) "Merged" else pull.state, statusColor(pull.state, pull.merged), statusFill(pull.state, pull.merged))
            }
            Text(
                pull.title,
                style = MaterialTheme.typography.titleMedium,
                color = strongText(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                BranchTag(pull.head, Modifier.weight(1f, fill = false))
                Text("into", style = MaterialTheme.typography.labelSmall, color = mutedText())
                BranchTag(pull.base, Modifier.weight(1f, fill = false))
            }
            Text(
                "${pull.author} opened ${formatDate(pull.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BranchCard(
    branch: Branch,
    selected: Boolean,
    isDefault: Boolean = false,
    onClick: () -> Unit,
) {
    PanelCard(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    branch.name,
                    color = if (selected) BrandOrange else strongText(0.74f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(shortSha(branch.sha), color = mutedText(), style = MaterialTheme.typography.labelSmall)
            }
            if (isDefault) {
                Tag("Default", DiffGreen, DiffGreenSoft)
                Spacer(Modifier.width(6.dp))
            }
            if (branch.protected) {
                NeutralTag("protected")
            }
        }
    }
}

@Composable
fun RepoFileCard(
    file: RepoTreeItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PanelCard(selected = selected, onClick = onClick) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                file.path,
                color = if (selected) BrandOrange else strongText(0.72f),
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                file.size?.let { "$it bytes" } ?: "file",
                color = mutedText(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun CodeBuffer(file: RepoFileContent) {
    PanelCard {
        Column(
            modifier = Modifier.padding(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                file.path,
                modifier = Modifier.padding(12.dp),
                color = strongText(0.86f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeSurface()),
            ) {
                val lines = file.content.lineSequence().take(120).toList()
                val truncated = file.content.lineSequence().drop(120).iterator().hasNext()
                lines.forEachIndexed { index, line ->
                    Text(
                        text = "${index + 1}".padStart(4) + "  " + line,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        color = codeText(0.9f),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (truncated) {
                    Text(
                        text = "File preview truncated on mobile.",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = mutedText(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceHeader(pull: PullRequest) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(BrandOrange),
            )
            Spacer(Modifier.width(8.dp))
            Text("Pull Request", color = mutedText(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Tag(if (pull.merged) "Merged" else pull.state, statusColor(pull.state, pull.merged), statusFill(pull.state, pull.merged))
        }
        Text(
            pull.title,
            color = strongText(),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${pull.number}", color = BrandOrange.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace)
            Text("${pull.base} -> ${pull.head}", color = mutedText(), style = MaterialTheme.typography.labelSmall)
        }
        if (pull.labels.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pull.labels.take(3).forEach { label ->
                    NeutralTag(label.name)
                }
            }
        }
    }
}

@Composable
fun WorkspaceTabs(
    activeTab: WorkspaceTab,
    counts: Map<WorkspaceTab, Int>,
    onSelect: (WorkspaceTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, outlineText(0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            WorkspaceTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) BrandOrange.copy(alpha = 0.035f) else Color.Transparent,
                    contentColor = if (selected) BrandOrange.copy(alpha = 0.82f) else mutedText(),
                ) {
                    Text(
                        text = tab.label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
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
fun FileCard(
    file: ChangedFile,
    highlightedLine: Int? = null,
    highlightedStartLine: Int? = null,
    highlightedSide: String? = null,
    highlightedStartSide: String? = null,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    file.filename,
                    modifier = Modifier.weight(1f),
                    color = strongText(0.86f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Tag(file.status, fileStatusColor(file.status), fileStatusFill(file.status))
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Tag("+${file.additions}", DiffGreen, DiffGreenSoft)
                Tag("-${file.deletions}", DiffRed, DiffRedSoft)
            }
            if (file.patch.isNotBlank()) {
                PatchPreview(
                    patch = file.patch,
                    highlightedLine = highlightedLine,
                    highlightedStartLine = highlightedStartLine,
                    highlightedSide = highlightedSide,
                    highlightedStartSide = highlightedStartSide,
                )
            }
        }
    }
}

@Composable
fun CommentCard(
    comment: PullComment,
    onJumpToDiff: (() -> Unit)? = null,
    onDraftFix: (() -> Unit)? = null,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author, color = strongText(), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                NeutralTag(if (comment.kind == CommentKind.Review) "Review" else "Comment")
                Spacer(Modifier.weight(1f))
                Text(formatDate(comment.createdAt), color = mutedText(), style = MaterialTheme.typography.labelSmall)
            }
            comment.path?.let {
                Text(
                    text = if (comment.line == null) it else "$it:${comment.line}",
                    color = BrandOrange.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                comment.body.ifBlank { "No comment body." },
                color = bodyText(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            if (comment.path != null && (onJumpToDiff != null || onDraftFix != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    onJumpToDiff?.let {
                        Surface(
                            modifier = Modifier.clickable(onClick = it),
                            shape = RoundedCornerShape(7.dp),
                            color = BrandOrange.copy(alpha = 0.055f),
                            border = BorderStroke(1.dp, BrandOrange.copy(alpha = 0.2f)),
                            contentColor = BrandOrange,
                        ) {
                            Text(
                                "Jump to diff",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    onDraftFix?.let {
                        Surface(
                            modifier = Modifier.clickable(onClick = it),
                            shape = RoundedCornerShape(7.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, outlineText(0.8f)),
                            contentColor = mutedText(),
                        ) {
                            Text(
                                "Draft fix",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewCard(review: PullReview) {
    PanelCard {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(review.author, color = strongText(), fontWeight = FontWeight.SemiBold)
                Text(formatDate(review.submittedAt), color = mutedText(), style = MaterialTheme.typography.labelSmall)
                if (review.body.isNotBlank()) {
                    Text(review.body, color = bodyText(), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Tag(review.state.lowercase(), reviewStateColor(review.state), reviewStateFill(review.state))
        }
    }
}

@Composable
fun CommitCard(commit: PullCommit) {
    PanelCard {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(commit.message.ifBlank { "Commit" }, color = strongText(0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${commit.author} - ${formatDate(commit.date)}", color = mutedText(), style = MaterialTheme.typography.labelSmall)
            }
            Text(shortSha(commit.sha), color = BrandOrange.copy(alpha = 0.72f), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun TimelineEventCard(event: TimelineEvent) {
    PanelCard {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StatusDot(event.kind, event.kind in setOf("merged", "closed", "reviewed", "commented", "committed"))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.label,
                        color = strongText(0.82f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    event.commitSha?.let { sha ->
                        Text(
                            shortSha(sha),
                            color = BrandOrange.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Text(
                    "${event.actor} - ${formatDate(event.date)}",
                    color = mutedText(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                event.body?.let { body ->
                    Text(
                        body,
                        color = bodyText(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun CheckCard(
    check: CheckRun,
    log: String? = null,
    logState: LoadState = LoadState.Idle,
    onLoadLog: (() -> Unit)? = null,
    onClearLog: (() -> Unit)? = null,
    onOpenAnnotation: ((String) -> Unit)? = null,
) {
    PanelCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(check.conclusion ?: check.status, check.conclusion == "success")
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(check.name, color = strongText(0.84f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(check.conclusion ?: check.status, color = mutedText(), style = MaterialTheme.typography.labelSmall)
                }
                check.completedAt?.let {
                    Text(formatDate(it), color = mutedText(), style = MaterialTheme.typography.labelSmall)
                }
            }
            check.summary?.let { summary ->
                Text(
                    cleanCheckOutput(summary),
                    color = bodyText(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            check.text?.let { text ->
                Text(
                    cleanCheckOutput(text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(codeSurface())
                        .padding(9.dp),
                    color = mutedText(0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (check.jobId != null && onLoadLog != null && log == null) {
                    SmallActionButton(
                        text = if (logState == LoadState.Loading) "Loading Log" else "Load Log",
                        selected = false,
                        onClick = {
                            if (logState != LoadState.Loading) onLoadLog()
                        },
                    )
                }
                if (log != null && onClearLog != null) {
                    SmallActionButton(
                        text = "Hide Log",
                        selected = false,
                        onClick = onClearLog,
                    )
                }
                Spacer(Modifier.weight(1f))
                when (logState) {
                    is LoadState.Failed -> Text(
                        logState.message,
                        color = DiffRed,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LoadState.Loading -> Text("Fetching raw job log", color = mutedText(), style = MaterialTheme.typography.labelSmall)
                    LoadState.Idle -> if (check.jobId == null) {
                        Text("No Actions job log", color = mutedText(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            log?.let { rawLog ->
                Text(
                    rawLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(codeSurface())
                        .padding(9.dp),
                    color = mutedText(0.88f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (check.annotations.isNotEmpty()) {
                Text(
                    "Annotations",
                    color = strongText(0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                check.annotations.take(3).forEach { annotation ->
                    CheckAnnotationRow(
                        annotation = annotation,
                        onClick = annotation.path.takeIf { it.isNotBlank() }?.let { path ->
                            onOpenAnnotation?.let { open -> { open(path) } }
                        },
                    )
                }
                if (check.annotations.size > 3) {
                    Text(
                        "+${check.annotations.size - 3} more annotations",
                        color = mutedText(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) BrandOrange.copy(alpha = 0.055f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) BrandOrange.copy(alpha = 0.22f) else outlineText(0.7f)),
        contentColor = if (selected) BrandOrange else mutedText(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CheckAnnotationRow(
    annotation: CheckAnnotation,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(codeSurface())
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(
                annotation.level.ifBlank { "annotation" },
                if (annotation.level == "failure") DiffRed else mutedText(),
                if (annotation.level == "failure") DiffRedSoft else MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append(annotation.path.ifBlank { "file" })
                    annotation.startLine?.let {
                        append(":")
                        append(it)
                        if (annotation.endLine != null && annotation.endLine != it) {
                            append("-")
                            append(annotation.endLine)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                color = BrandOrange.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        annotation.title?.let {
            Text(
                it,
                color = strongText(0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            cleanCheckOutput(annotation.message),
            color = mutedText(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionTitle(title: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        count?.let {
            Text(
                it.toString(),
                color = mutedText(0.64f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun FileManifestItem(
    file: ChangedFile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) BrandOrange.copy(alpha = 0.05f) else Color.Transparent,
        border = BorderStroke(1.dp, outlineText(0.7f)),
        shape = RoundedCornerShape(8.dp),
        contentColor = strongText(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                file.filename,
                color = if (selected) BrandOrange else strongText(0.72f),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("+${file.additions}", color = DiffGreen.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                Text("-${file.deletions}", color = DiffRed.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                Text(file.status, color = mutedText(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LoadingRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandOrange)
        Spacer(Modifier.width(8.dp))
        Text(label, color = mutedText(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EmptyPanel(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        color = mutedText(0.78f),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun ErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = DiffRedSoft,
        border = BorderStroke(1.dp, DiffRed.copy(alpha = 0.28f)),
        contentColor = DiffRed,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Tag(text: String, foreground: Color, background: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = background.copy(alpha = background.alpha * 0.72f),
        contentColor = foreground.copy(alpha = 0.82f),
    ) {
        Text(
            text = text.lowercase(),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BranchTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = BrandOrange.copy(alpha = 0.055f),
        contentColor = BrandOrange.copy(alpha = 0.68f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusDot(state: String, merged: Boolean) {
    val color = statusColor(state, merged)
    Spacer(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

private data class PatchLineData(
    val text: String,
    val oldLine: Int?,
    val newLine: Int?,
)

private fun parsePatchLines(patch: String): List<PatchLineData> {
    val hunkRegex = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*""")
    var nextOldLine: Int? = null
    var nextNewLine: Int? = null
    return patch.lineSequence().take(120).map { line ->
        val hunk = hunkRegex.matchEntire(line)
        if (hunk != null) {
            nextOldLine = hunk.groupValues.getOrNull(1)?.toIntOrNull()
            nextNewLine = hunk.groupValues.getOrNull(2)?.toIntOrNull()
            PatchLineData(line, null, null)
        } else {
            val currentOldLine = when {
                line.startsWith("+") -> null
                nextOldLine != null -> nextOldLine
                else -> null
            }
            val currentNewLine = when {
                line.startsWith("-") -> null
                nextNewLine != null -> nextNewLine
                else -> null
            }
            if (!line.startsWith("+") && nextOldLine != null) {
                nextOldLine = nextOldLine!! + 1
            }
            if (!line.startsWith("-") && nextNewLine != null) {
                nextNewLine = nextNewLine!! + 1
            }
            PatchLineData(line, currentOldLine, currentNewLine)
        }
    }.toList()
}

@Composable
private fun PatchPreview(
    patch: String,
    highlightedLine: Int?,
    highlightedStartLine: Int?,
    highlightedSide: String?,
    highlightedStartSide: String?,
) {
    val lines = parsePatchLines(patch)
    val truncated = patch.lineSequence().drop(120).iterator().hasNext()
    val side = highlightedSide?.uppercase() ?: "RIGHT"
    val startSide = highlightedStartSide?.uppercase() ?: side
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(codeSurface()),
    ) {
        lines.forEach { line ->
            val highlighted = isHighlightedPatchLine(
                line = line,
                highlightedLine = highlightedLine,
                highlightedStartLine = highlightedStartLine,
                side = side,
                startSide = startSide,
            )
            PatchLine(line.text, highlighted)
        }
        if (truncated) {
            Text(
                text = "Diff truncated on mobile.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = mutedText(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun isHighlightedPatchLine(
    line: PatchLineData,
    highlightedLine: Int?,
    highlightedStartLine: Int?,
    side: String,
    startSide: String,
): Boolean {
    if (highlightedLine == null) return false
    val endLineNumber = lineNumberForSide(line, side)
    val startLine = highlightedStartLine ?: highlightedLine
    if (startSide != side) {
        return endLineNumber == highlightedLine ||
            lineNumberForSide(line, startSide) == startLine
    }
    val rangeStart = minOf(startLine, highlightedLine)
    val rangeEnd = maxOf(startLine, highlightedLine)
    return endLineNumber != null && endLineNumber in rangeStart..rangeEnd
}

private fun lineNumberForSide(line: PatchLineData, side: String): Int? =
    if (side == "LEFT") line.oldLine else line.newLine

@Composable
private fun PatchLine(line: String, highlighted: Boolean) {
    val background = when {
        highlighted -> DiffHighlight.copy(alpha = 0.30f)
        line.startsWith("+") -> DiffGreenSoft
        line.startsWith("-") -> DiffRedSoft
        line.startsWith("@@") -> DiffBlueSoft
        else -> Color.Transparent
    }
    val foreground = when {
        highlighted -> DiffHighlightText
        line.startsWith("+") -> DiffGreen.copy(alpha = 0.86f)
        line.startsWith("-") -> DiffRed.copy(alpha = 0.86f)
        line.startsWith("@@") -> DiffBlue.copy(alpha = 0.78f)
        else -> codeText(0.88f)
    }
    Text(
        text = line,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = foreground,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun statusColor(state: String, merged: Boolean): Color = when {
    merged -> DiffBlue
    state == "open" || state == "success" -> DiffGreen
    state == "closed" || state == "failure" || state == "cancelled" -> DiffRed
    else -> TextMuted
}

private fun statusFill(state: String, merged: Boolean): Color = when {
    merged -> DiffBlueSoft
    state == "open" || state == "success" -> DiffGreenSoft
    state == "closed" || state == "failure" || state == "cancelled" -> DiffRedSoft
    else -> PanelRaised
}

private fun fileStatusColor(status: String): Color = when (status) {
    "added" -> DiffGreen
    "removed" -> DiffRed
    "modified" -> DiffBlue
    else -> TextMuted
}

private fun fileStatusFill(status: String): Color = when (status) {
    "added" -> DiffGreenSoft
    "removed" -> DiffRedSoft
    "modified" -> DiffBlueSoft
    else -> PanelRaised
}

private fun reviewStateColor(state: String): Color = when (state.lowercase()) {
    "approved" -> DiffGreen
    "changes_requested" -> DiffRed
    else -> TextMuted
}

private fun reviewStateFill(state: String): Color = when (state.lowercase()) {
    "approved" -> DiffGreenSoft
    "changes_requested" -> DiffRedSoft
    else -> PanelRaised
}

private fun cleanCheckOutput(value: String): String =
    value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(20)
        .joinToString("\n")
