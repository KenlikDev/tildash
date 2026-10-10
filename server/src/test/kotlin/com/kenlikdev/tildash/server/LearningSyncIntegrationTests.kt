package com.kenlikdev.tildash.server

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
class LearningSyncIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun createPublishedLessonFixture() {
        jdbcTemplate.execute("TRUNCATE tildash.learning_attempts")
        jdbcTemplate.execute("TRUNCATE tildash.content_nodes CASCADE")
        val courseId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099")
        val lessonId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        insertNode(courseId, "COURSE", null, 0)
        insertNode(lessonId, "LESSON", courseId, 0)
        publish(courseId, 1, "Course")
        val lessonVersionId = publish(lessonId, 1, "Lesson")
        insertPublishedExercise(
            publishedVersionId = lessonVersionId,
            contentNodeId = lessonId,
            exerciseId = "exercise-1",
            prompt = "Translate hello.",
            expectedAnswers = """["hello"]""",
        )
    }

    @Test
    fun syncRequiresAuthentication() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-unauthenticated")),
            ).andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:unauthorized"))
    }

    @Test
    fun nonLearnerCannotUseLearningSync() {
        mockMvc
            .perform(
                post("/api/v1/learning/sync")
                    .with(user("teacher-a").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson("attempt-teacher")),
            ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:forbidden"))
    }

    @Test
    fun newAttemptIsAcknowledgedAndPersisted() {
        mockMvc
            .perform(authenticatedSync("learner-a", requestJson("attempt-new")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-new"))
            .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)

        assertEquals(1, countAttempts("learner-a", "attempt-new"))
    }

    @Test
    fun serverReevaluatesClientReportedOutcomeAgainstPublishedExercise() {
        val lessonId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val versionTwoId = publish(lessonId, 2, "Lesson version two")
        insertPublishedExercise(
            publishedVersionId = versionTwoId,
            contentNodeId = lessonId,
            exerciseId = "exercise-1",
            prompt = "Translate hello.",
            expectedAnswers = """["world"]""",
        )

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        attemptId = "attempt-falsely-reported-correct",
                        outcome = "CORRECT",
                        responseValue = "wrong",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-falsely-reported-correct"))

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        attemptId = "attempt-falsely-reported-incorrect",
                        outcome = "INCORRECT",
                        responseValue = "hello",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-falsely-reported-incorrect"))

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        attemptId = "attempt-v2-world",
                        outcome = "INCORRECT",
                        responseValue = "world",
                        publishedVersion = 2,
                    ),
                ),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        attemptId = "attempt-v2-hello",
                        outcome = "CORRECT",
                        responseValue = "hello",
                        publishedVersion = 2,
                    ),
                ),
            ).andExpect(status().isOk)

        assertEquals("INCORRECT", findOutcome("learner-a", "attempt-falsely-reported-correct"))
        assertEquals("CORRECT", findOutcome("learner-a", "attempt-falsely-reported-incorrect"))
        assertEquals("CORRECT", findOutcome("learner-a", "attempt-v2-world"))
        assertEquals("INCORRECT", findOutcome("learner-a", "attempt-v2-hello"))
    }

    @Test
    fun identicalRetryIsAcknowledgedWithoutDuplicateRow() {
        val request = requestJson("attempt-idempotent")

        repeat(2) {
            mockMvc
                .perform(authenticatedSync("learner-a", request))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-idempotent"))
                .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)
        }

        assertEquals(1, countAttempts("learner-a", "attempt-idempotent"))
    }

    @Test
    fun identicalRetryWithNanosecondTimestampIsAcknowledged() {
        val request =
            requestJson(
                attemptId = "attempt-high-precision-time",
                occurredAt = "2026-09-28T09:00:00.123456789Z",
            )

        repeat(2) {
            mockMvc
                .perform(authenticatedSync("learner-a", request))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-high-precision-time"))
                .andExpect(jsonPath("$.conflictAttemptIds").isEmpty)
        }

        assertEquals(
            "2026-09-28 09:00:00.123456",
            jdbcTemplate.queryForObject(
                "select to_char(occurred_at at time zone 'UTC', " +
                    "'YYYY-MM-DD HH24:MI:SS.US') " +
                    "from tildash.learning_attempts " +
                    "where learner_subject = ? and attempt_id = ?",
                String::class.java,
                "learner-a",
                "attempt-high-precision-time",
            ),
        )
    }

    @Test
    fun sameAttemptIdAndExerciseIdAcrossLessonsIsReportedAsConflict() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-cross-lesson",
                        lessonId = "550e8400-e29b-41d4-a716-446655440000",
                    ),
                ),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-cross-lesson",
                        lessonId = "550e8400-e29b-41d4-a716-446655440001",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-cross-lesson"))

        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            jdbcTemplate.queryForObject(
                "select lesson_id::text from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
                String::class.java,
                "learner-a",
                "attempt-cross-lesson",
            ),
        )
    }

    @Test
    fun conflictingRetryIsReportedAndExistingPayloadIsPreserved() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-conflict",
                        "CORRECT",
                        "2026-09-28T09:00:00Z",
                    ),
                ),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-conflict",
                        "INCORRECT",
                        "2026-09-28T10:00:00Z",
                    ),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-conflict"))

        assertEquals("CORRECT", findOutcome("learner-a", "attempt-conflict"))
    }

    @Test
    fun contradictoryDuplicateAttemptIdsInOneBatchAreReportedAsConflict() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    contradictoryDuplicateRequest(),
                ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds").isEmpty)
            .andExpect(jsonPath("$.conflictAttemptIds[0]").value("attempt-duplicate"))

        assertEquals(0, countAttempts("learner-a", "attempt-duplicate"))
    }

    @Test
    fun sameAttemptIdIsScopedToAuthenticatedLearner() {
        val request = requestJson("attempt-shared-id")

        mockMvc
            .perform(authenticatedSync("learner-a", request))
            .andExpect(status().isOk)

        mockMvc
            .perform(authenticatedSync("learner-b", request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.acknowledgedAttemptIds[0]").value("attempt-shared-id"))

        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "select count(*) from tildash.learning_attempts where attempt_id = ?",
                Int::class.java,
                "attempt-shared-id",
            ),
        )
    }

    @Test
    fun persistedLearningAttemptCannotBeUpdatedOrDeleted() {
        mockMvc
            .perform(authenticatedSync("learner-a", requestJson("attempt-immutable")))
            .andExpect(status().isOk)

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "update tildash.learning_attempts set outcome = 'INCORRECT' where learner_subject = ? and attempt_id = ?",
                "learner-a",
                "attempt-immutable",
            )
        }

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "delete from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
                "learner-a",
                "attempt-immutable",
            )
        }

        assertEquals("CORRECT", findOutcome("learner-a", "attempt-immutable"))
    }

    @Test
    fun invalidDeviceIdUsesProblemDetails() {
        mockMvc
            .perform(
                authenticatedSync(
                    "learner-a",
                    requestJson(
                        "attempt-invalid-device",
                        deviceId = " ",
                    ),
                ),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    private fun insertNode(
        id: UUID,
        kind: String,
        parentId: UUID?,
        position: Int,
    ) {
        jdbcTemplate.update(
            """
            insert into tildash.content_nodes (
                id, kind, parent_id, source_locale, state, position
            )
            values (?::uuid, ?, ?::uuid, ?, 'PUBLISHED', ?)
            """.trimIndent(),
            id,
            kind,
            parentId,
            "crh",
            position,
        )
    }

    private fun publish(
        contentId: UUID,
        version: Int,
        title: String,
    ): UUID {
        val provenanceId = UUID.randomUUID()
        val sourceRevisionId = UUID.randomUUID()
        val publishedVersionId = UUID.randomUUID()
        val now = Timestamp.from(Instant.parse("2026-10-01T08:00:00Z"))

        jdbcTemplate.update(
            """
            insert into tildash.content_provenance (
                id, source_title, copyright_status
            )
            values (?::uuid, ?, 'PUBLIC_DOMAIN')
            """.trimIndent(),
            provenanceId,
            "Learning sync test source",
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_source_revisions (
                id, content_node_id, revision_no, payload, provenance_id, created_by, created_at
            )
            values (?::uuid, ?::uuid, ?, cast(? as jsonb), ?::uuid, ?, ?)
            """.trimIndent(),
            sourceRevisionId,
            contentId,
            version,
            """{"value":"$title"}""",
            provenanceId,
            "integration-test",
            now,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_published_versions (
                id, content_node_id, version_no, source_revision_id, provenance_id, published_by, published_at
            )
            values (?::uuid, ?::uuid, ?, ?::uuid, ?::uuid, ?, ?)
            """.trimIndent(),
            publishedVersionId,
            contentId,
            version,
            sourceRevisionId,
            provenanceId,
            "integration-reviewer",
            now,
        )
        return publishedVersionId
    }

    private fun insertPublishedExercise(
        publishedVersionId: UUID,
        contentNodeId: UUID,
        exerciseId: String,
        prompt: String,
        expectedAnswers: String,
    ) {
        jdbcTemplate.update(
            """
            insert into tildash.content_published_exercises (
                published_version_id, content_node_id, exercise_id, position,
                exercise_type, prompt, expected_answers
            )
            values (?::uuid, ?::uuid, ?, 0, 'MANUAL_INPUT', ?, cast(? as jsonb))
            """.trimIndent(),
            publishedVersionId,
            contentNodeId,
            exerciseId,
            prompt,
            expectedAnswers,
        )
    }

    private fun authenticatedSync(
        subject: String,
        request: String,
    ) = post("/api/v1/learning/sync")
        .with(user(subject).roles("LEARNER"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(request)

    private fun countAttempts(
        subject: String,
        attemptId: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
            Int::class.java,
            subject,
            attemptId,
        ) ?: 0

    private fun findOutcome(
        subject: String,
        attemptId: String,
    ): String =
        jdbcTemplate.queryForObject(
            "select outcome from tildash.learning_attempts where learner_subject = ? and attempt_id = ?",
            String::class.java,
            subject,
            attemptId,
        ) ?: error("Learning attempt outcome is missing.")

    private fun contradictoryDuplicateRequest(): String =
        """
        {
          "deviceId": "device-a",
          "attempts": [
            {
              "attemptId": "attempt-duplicate",
              "lessonId": "550e8400-e29b-41d4-a716-446655440000",
              "publishedVersion": 1,
              "exerciseId": "exercise-1",
              "response": {"type": "TEXT", "value": "hello"},
              "outcome": "CORRECT",
              "occurredAt": "2026-09-28T09:00:00Z"
            },
            {
              "attemptId": "attempt-duplicate",
              "lessonId": "550e8400-e29b-41d4-a716-446655440000",
              "publishedVersion": 1,
              "exerciseId": "exercise-1",
              "response": {"type": "TEXT", "value": "wrong"},
              "outcome": "INCORRECT",
              "occurredAt": "2026-09-28T10:00:00Z"
            }
          ]
        }
        """.trimIndent()

    private fun requestJson(
        attemptId: String,
        outcome: String = "CORRECT",
        occurredAt: String = "2026-09-28T09:00:00Z",
        deviceId: String = "device-a",
        lessonId: String = "550e8400-e29b-41d4-a716-446655440000",
        responseValue: String = "hello",
        publishedVersion: Int = 1,
    ): String =
        """
        {
          "deviceId": "PLACEHOLDER_DEVICE",
          "attempts": [
            {
              "attemptId": "PLACEHOLDER_ATTEMPT",
              "lessonId": "PLACEHOLDER_LESSON",
              "publishedVersion": PLACEHOLDER_PUBLISHED_VERSION,
              "exerciseId": "exercise-1",
              "response": {"type": "TEXT", "value": "PLACEHOLDER_RESPONSE"},
              "outcome": "PLACEHOLDER_OUTCOME",
              "occurredAt": "PLACEHOLDER_OCCURRED_AT"
            }
          ]
        }
        """.trimIndent()
            .replace("PLACEHOLDER_DEVICE", deviceId)
            .replace("PLACEHOLDER_ATTEMPT", attemptId)
            .replace("PLACEHOLDER_LESSON", lessonId)
            .replace("PLACEHOLDER_PUBLISHED_VERSION", publishedVersion.toString())
            .replace("PLACEHOLDER_OUTCOME", outcome)
            .replace("PLACEHOLDER_OCCURRED_AT", occurredAt)
            .replace("PLACEHOLDER_RESPONSE", responseValue)
}
