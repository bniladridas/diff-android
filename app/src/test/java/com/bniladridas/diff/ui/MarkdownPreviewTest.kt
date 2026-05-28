package com.bniladridas.diff.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPreviewTest {
    @Test
    fun `hides markdown image urls behind badge labels`() {
        val body = """
            ![CI badge](https://img.shields.io/badge/ci-passing-brightgreen)
            Ready for review.
        """.trimIndent()

        assertEquals(
            "badge: CI\nReady for review.",
            markdownBodyPreview(body, "empty"),
        )
    }

    @Test
    fun `uses markdown link text instead of raw urls`() {
        val body = "See [release notes](https://github.com/example/repo/releases/tag/v1) and https://github.com/example/repo."

        assertEquals(
            "See release notes and github.com",
            markdownBodyPreview(body, "empty"),
        )
    }

    @Test
    fun `hides html image badges behind alt labels`() {
        val body = """<img src="https://img.shields.io/badge/status-ready-green" alt="Status badge">"""

        assertEquals(
            "badge: Status",
            markdownBodyPreview(body, "empty"),
        )
    }

    @Test
    fun `keeps blank previews calm`() {
        assertEquals("No comment body.", markdownBodyPreview("   ", "No comment body."))
    }
}
