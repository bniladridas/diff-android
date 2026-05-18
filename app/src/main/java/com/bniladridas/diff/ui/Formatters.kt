package com.bniladridas.diff.ui

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun formatDate(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrElse {
        value.take(10)
    }
}

fun shortSha(value: String): String = value.take(7)

