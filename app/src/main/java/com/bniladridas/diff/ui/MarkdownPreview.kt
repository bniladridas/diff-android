package com.bniladridas.diff.ui

private val markdownImagePattern = Regex("""!\[([^\]]*)]\([^)]+\)""")
private val markdownLinkPattern = Regex("""\[([^\]]+)]\(https?://[^)]+\)""")
private val htmlImagePattern = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val htmlAltPattern = Regex("""\balt=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val bareUrlPattern = Regex("""https?://[^\s)>\]]+""")

internal fun markdownBodyPreview(
    rawBody: String,
    emptyText: String,
): String {
    if (rawBody.isBlank()) return emptyText

    return rawBody
        .replace(htmlImagePattern) { match ->
            htmlAltPattern.find(match.value)?.groups?.get(1)?.value?.cleanBadgeLabel()?.let { "badge: $it" }.orEmpty()
        }
        .replace(markdownImagePattern) { match ->
            match.groups[1]?.value?.cleanBadgeLabel()?.let { "badge: $it" }.orEmpty()
        }
        .replace(markdownLinkPattern) { match ->
            match.groups[1]?.value?.trim().orEmpty()
        }
        .replace(bareUrlPattern) { match ->
            match.value.displayUrl()
        }
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .ifBlank { emptyText }
}

private fun String.cleanBadgeLabel(): String? {
    val cleaned = trim()
        .removeSuffix(" badge")
        .removeSuffix(" Badge")
        .takeIf { it.isNotBlank() && it != "-" }

    return cleaned
}

private fun String.displayUrl(): String {
    val withoutScheme = removePrefix("https://").removePrefix("http://")
    return withoutScheme.substringBefore('/').ifBlank { "link" }
}
