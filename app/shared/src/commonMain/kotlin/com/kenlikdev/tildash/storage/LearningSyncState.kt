package com.kenlikdev.tildash.storage

sealed interface LearningSyncState {
    data object Idle : LearningSyncState

    data class Running(
        val pendingCount: Long,
    ) : LearningSyncState

    data class Retrying(
        val retryCount: Int,
        val delayMillis: Long,
        val pendingCount: Long,
    ) : LearningSyncState

    data class Succeeded(
        val report: LearningSyncReport,
    ) : LearningSyncState

    data class Conflicted(
        val report: LearningSyncReport,
    ) : LearningSyncState

    data class Failed(
        val message: String,
        val retryable: Boolean,
        val details: LearningSyncFailureDetails?,
    ) : LearningSyncState
}

fun interface LearningSyncObserver {
    fun onStateChanged(state: LearningSyncState)
}
