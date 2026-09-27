package com.kenlikdev.tildash.server.security

import java.util.Locale

enum class Role(
    val tokenValue: String,
    val authority: String,
) {
    LEARNER("learner", "ROLE_LEARNER"),
    TEACHER("teacher", "ROLE_TEACHER"),
    REVIEWER("reviewer", "ROLE_REVIEWER"),
    ADMINISTRATOR("administrator", "ROLE_ADMINISTRATOR"),
    ;

    companion object {
        fun fromTokenValue(value: String): Role? = entries.firstOrNull { it.tokenValue == value.trim().lowercase(Locale.ROOT) }

        fun fromAuthority(value: String): Role? = entries.firstOrNull { it.authority == value }
    }
}
