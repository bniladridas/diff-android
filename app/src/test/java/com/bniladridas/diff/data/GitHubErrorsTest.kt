package com.bniladridas.diff.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubErrorsTest {
    @Test
    fun formatsAuthFailure() {
        assertEquals(
            "GitHub authentication failed. Check the token in Account.",
            GitHubErrors.format(401, """{"message":"Bad credentials"}"""),
        )
    }

    @Test
    fun formatsRateLimitWithActionableCopy() {
        val message = GitHubErrors.format(
            code = 403,
            body = """{"message":"API rate limit exceeded for 127.0.0.1."}""",
            resetEpochSeconds = 1_700_000_000,
        )

        assertTrue(message.startsWith("GitHub rate limit exceeded."))
        assertTrue(message.contains("Add a token in Account for a higher limit."))
    }

    @Test
    fun formatsConflict() {
        assertEquals(
            "GitHub reported a conflict. Refresh and check branch state.",
            GitHubErrors.format(409, """{"message":"Conflict"}"""),
        )
    }

    @Test
    fun formatsValidationWithGitHubMessage() {
        assertEquals(
            "GitHub rejected the request: Validation Failed",
            GitHubErrors.format(422, """{"message":"Validation Failed"}"""),
        )
    }
}
