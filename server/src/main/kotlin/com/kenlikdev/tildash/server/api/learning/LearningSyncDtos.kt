
package com.kenlikdev.tildash.server.api.learning

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class LearningSyncRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val deviceId: String,
    @field:NotEmpty
    @field:Size(max = 100)
    val attempts: List<@Valid LearningAttemptRequest>,
)

data class LearningAttemptRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val attemptId: String,
    val lessonId: UUID,
    @field:NotBlank
    @field:Size(max = 200)
    val exerciseId: String,
    @field:Valid
    val response: LearningResponseRequest,
    val outcome: LearningOutcomeRequest,
    val occurredAt: OffsetDateTime,
)

data class LearningResponseRequest(
    val type: LearningResponseType,
    val value: String,
)

enum class LearningResponseType {
    TEXT,
}

enum class LearningOutcomeRequest {
    CORRECT,
    INCORRECT,
}

data class LearningSyncResponse(
    val acknowledgedAttemptIds: List<String>,
    val conflictAttemptIds: List<String>,
)
