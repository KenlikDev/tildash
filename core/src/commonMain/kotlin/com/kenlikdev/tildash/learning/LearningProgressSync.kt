package com.kenlikdev.tildash.learning

data class LearningProgressSyncBatch(
    val deviceId: String,
    val attempts: List<LearningAttempt>,
) {
    init {
        require(deviceId.isNotBlank()) {
            "Sync device ID must not be blank."
        }
    }
}

data class LearningProgressSyncConflict(
    val attemptId: String,
    val existing: LearningAttempt,
    val incoming: LearningAttempt,
)

data class LearningProgressSyncResult(
    val progress: LearningProgress,
    val acknowledgedAttemptIds: List<String>,
    val conflicts: List<LearningProgressSyncConflict>,
) {
    val hasConflicts: Boolean
        get() = conflicts.isNotEmpty()

    val canAcknowledgeBatch: Boolean
        get() = conflicts.isEmpty()
}

object LearningProgressSync {
    private val attemptComparator =
        compareBy<LearningAttempt> { it.occurredAt }
            .thenBy { it.attemptId }

    fun merge(
        local: LearningProgress,
        incoming: LearningProgressSyncBatch,
    ): LearningProgressSyncResult {
        val localById = local.attempts.associateBy { it.attemptId }
        val incomingById =
            incoming.attempts
                .groupBy { it.attemptId }
                .entries
                .sortedBy { it.key }

        val conflicts = mutableListOf<LearningProgressSyncConflict>()
        val accepted = mutableListOf<LearningAttempt>()
        val acknowledgedIds = mutableListOf<String>()

        incomingById.forEach { entry ->
            val attemptId = entry.key
            val candidates = entry.value
            val first = candidates.first()
            val duplicateBatchEntry = candidates.drop(1).firstOrNull { it != first }

            if (duplicateBatchEntry != null) {
                conflicts +=
                    LearningProgressSyncConflict(
                        attemptId = attemptId,
                        existing = first,
                        incoming = duplicateBatchEntry,
                    )
                return@forEach
            }

            val existing = localById[attemptId]
            when {
                existing == null -> {
                    accepted += first
                    acknowledgedIds += attemptId
                }

                existing == first -> {
                    acknowledgedIds += attemptId
                }

                else -> {
                    conflicts +=
                        LearningProgressSyncConflict(
                            attemptId = attemptId,
                            existing = existing,
                            incoming = first,
                        )
                }
            }
        }

        val mergedAttempts =
            (local.attempts + accepted)
                .sortedWith(attemptComparator)

        return LearningProgressSyncResult(
            progress = LearningProgress.fromCanonicalAttempts(mergedAttempts),
            acknowledgedAttemptIds = acknowledgedIds.sorted(),
            conflicts = conflicts.sortedBy { it.attemptId },
        )
    }
}
