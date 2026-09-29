package com.kenlikdev.tildash.storage

data class LearningSyncFailureDetails(
    val type: String?,
    val title: String?,
    val status: Int?,
    val detail: String?,
    val instance: String?,
)

class TransientLearningSyncFailure(
    message: String,
    cause: Throwable? = null,
    val details: LearningSyncFailureDetails? = null,
) : Exception(message, cause)
