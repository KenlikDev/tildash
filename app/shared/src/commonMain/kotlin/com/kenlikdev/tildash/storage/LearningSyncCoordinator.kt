package com.kenlikdev.tildash.storage

import com.kenlikdev.tildash.learning.LearningProgressSyncBatch

data class LearningSyncRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 1_000,
    val multiplier: Int = 2,
    val maxDelayMillis: Long = 30_000,
) {
    init {
        require(maxAttempts > 0) {
            "maxAttempts must be positive."
        }
        require(initialDelayMillis >= 0) {
            "initialDelayMillis must not be negative."
        }
        require(multiplier >= 1) {
            "multiplier must be at least 1."
        }
        require(maxDelayMillis >= initialDelayMillis) {
            "maxDelayMillis must be greater than or equal to initialDelayMillis."
        }
    }

    fun delayBeforeRetry(retryNumber: Int): Long {
        require(retryNumber >= 1) {
            "retryNumber must be positive."
        }

        var delay = initialDelayMillis
        repeat(retryNumber - 1) {
            delay = (delay * multiplier).coerceAtMost(maxDelayMillis)
        }
        return delay
    }
}

sealed interface LearningSyncTransportResult {
    data class Succeeded(
        val acknowledgedAttemptIds: List<String>,
        val conflictAttemptIds: List<String> = emptyList(),
    ) : LearningSyncTransportResult
}

interface LearningSyncTransport {
    suspend fun synchronize(batch: LearningProgressSyncBatch): LearningSyncTransportResult
}

class TransientLearningSyncFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class LearningSyncReport(
    val submittedAttemptIds: List<String>,
    val acknowledgedAttemptIds: List<String>,
    val conflictAttemptIds: List<String>,
    val retryCount: Int,
) {
    val hasConflicts: Boolean
        get() = conflictAttemptIds.isNotEmpty()

    val hasAcknowledgements: Boolean
        get() = acknowledgedAttemptIds.isNotEmpty()
}

class LearningSyncCoordinator(
    private val store: LearningProgressStore,
    private val transport: LearningSyncTransport,
    private val retryPolicy: LearningSyncRetryPolicy = LearningSyncRetryPolicy(),
    private val delayBeforeRetry: suspend (Long) -> Unit,
) {
    suspend fun synchronize(deviceId: String): LearningSyncReport {
        require(deviceId.isNotBlank()) {
            "Sync device ID must not be blank."
        }

        val pendingAttempts = store.loadPendingSyncAttempts()
        if (pendingAttempts.isEmpty()) {
            return LearningSyncReport(
                submittedAttemptIds = emptyList(),
                acknowledgedAttemptIds = emptyList(),
                conflictAttemptIds = emptyList(),
                retryCount = 0,
            )
        }

        val batch =
            LearningProgressSyncBatch(
                deviceId = deviceId,
                attempts = pendingAttempts,
            )

        var attemptNumber = 1
        var retryCount = 0

        while (true) {
            try {
                return synchronizeOnce(batch, retryCount)
            } catch (failure: TransientLearningSyncFailure) {
                if (attemptNumber >= retryPolicy.maxAttempts) {
                    throw failure
                }

                retryCount += 1
                delayBeforeRetry(retryPolicy.delayBeforeRetry(retryCount))
                attemptNumber += 1
            }
        }
    }

    private suspend fun synchronizeOnce(
        batch: LearningProgressSyncBatch,
        retryCount: Int,
    ): LearningSyncReport {
        val pendingIds = batch.attempts.map { it.attemptId }.toSet()
        val result = transport.synchronize(batch)

        val (acknowledgedIds, conflictIds) =
            when (result) {
                is LearningSyncTransportResult.Succeeded ->
                    result.acknowledgedAttemptIds.distinct().sorted() to
                        result.conflictAttemptIds.distinct().sorted()
            }

        require(acknowledgedIds.all { it in pendingIds }) {
            "Transport acknowledged an attempt that was not part of the submitted batch."
        }
        require(conflictIds.all { it in pendingIds }) {
            "Transport reported a conflict for an attempt that was not part of the submitted batch."
        }
        require(acknowledgedIds.intersect(conflictIds).isEmpty()) {
            "An attempt cannot be acknowledged and conflicted in the same sync result."
        }

        acknowledgedIds.forEach(store::acknowledgeAttempt)

        return LearningSyncReport(
            submittedAttemptIds = pendingIds.toList().sorted(),
            acknowledgedAttemptIds = acknowledgedIds,
            conflictAttemptIds = conflictIds,
            retryCount = retryCount,
        )
    }
}
