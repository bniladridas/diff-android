package com.bniladridas.diff.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabasePreferencesApiTest {
    @Test
    fun detectsPostgrestMissingAiDraftsColumn() {
        assertTrue(
            SupabasePreferencesApi.isMissingAiDraftsColumnForTest(
                "PGRST204: Could not find the 'ai_drafts' column of 'user_preferences' in the schema cache",
            ),
        )
    }

    @Test
    fun detectsPostgresMissingAiDraftsColumn() {
        assertTrue(
            SupabasePreferencesApi.isMissingAiDraftsColumnForTest(
                "42703: column user_preferences.ai_drafts does not exist",
            ),
        )
    }

    @Test
    fun ignoresUnrelatedSupabaseErrors() {
        assertFalse(
            SupabasePreferencesApi.isMissingAiDraftsColumnForTest(
                "PGRST301: JWT expired",
            ),
        )
    }
}
