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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
class ContentStudioIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clearFixtures() {
        jdbcTemplate.execute("TRUNCATE tildash.content_nodes CASCADE")
    }

    @Test
    fun nonLessonContentCannotEnterContentWorkflow() {
        val courseId = createNode("COURSE", null, "Course")
        val exampleId = createNode("EXAMPLE", courseId, "Example")

        mockMvc
            .perform(
                post("/api/v1/content/$exampleId/submit")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isConflict())
    }

    @Test
    fun lessonWithoutExercisesCannotBeSubmitted() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId =
            createNode(
                kind = "LESSON",
                parentId = courseId,
                value = "Empty lesson",
                seedExercise = false,
            )

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:validation-failed"))
            .andExpect(jsonPath("$.errors[0].code").value("EMPTY_LESSON_EXERCISES"))
    }

    @Test
    fun teacherCanCreateUpdateAndDeleteDraftLessonExerciseAndPublishedSnapshotKeepsIt() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                put("/api/v1/content/$lessonId/exercises/exercise-1")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "prompt" to "Write the greeting.",
                                "position" to 0,
                                "expectedAnswers" to listOf("merhaba", "selam"),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.prompt").value("Write the greeting."))
            .andExpect(jsonPath("$.expectedAnswers[1]").value("selam"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/exercises")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "id" to "exercise-2",
                                "prompt" to "Write goodbye.",
                                "position" to 1,
                                "expectedAnswers" to listOf("hoşça qal"),
                            ),
                        ),
                    ),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/exercises")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].id").value("exercise-2"))

        mockMvc
            .perform(
                delete("/api/v1/content/$lessonId/exercises/exercise-2")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isOk)

        publishLesson(lessonId)

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/exercises")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].expectedAnswers.length()").value(2))

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from tildash.content_published_exercises cpe
                join tildash.content_published_versions pv on pv.id = cpe.published_version_id
                where pv.content_node_id = ?::uuid
                """.trimIndent(),
                Int::class.java,
                lessonId,
            ),
        )

        mockMvc
            .perform(
                put("/api/v1/content/$lessonId/exercises/exercise-1")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "prompt" to "Tamper after publish.",
                                "position" to 0,
                                "expectedAnswers" to listOf("tampered"),
                            ),
                        ),
                    ),
            ).andExpect(status().isConflict())

        mockMvc
            .perform(
                delete("/api/v1/content/$lessonId/exercises/exercise-1")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isConflict())
    }

    @Test
    fun publishedLessonIsExposedThroughLearnerPackageAfterAuthoringLifecycle() {
        val courseId = createNode("COURSE", null, "Course", seedExercise = false)
        val lessonId = createNode("LESSON", courseId, "Lesson")

        publishCourse(courseId)
        publishLesson(lessonId)

        mockMvc
            .perform(
                get("/api/v1/learning/lessons/$lessonId")
                    .with(user("learner").roles("LEARNER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.course.id").value(courseId))
            .andExpect(jsonPath("$.lesson.id").value(lessonId))
            .andExpect(jsonPath("$.lesson.title").value("Lesson"))
            .andExpect(jsonPath("$.exercises.length()").value(1))
            .andExpect(jsonPath("$.exercises[0].id").value("exercise-1"))
            .andExpect(jsonPath("$.exercises[0].prompt").value("Translate hello."))
            .andExpect(jsonPath("$.exercises[0].expectedAnswers[0]").value("merhaba"))
    }

    @Test
    fun teacherCanPublishCourseThroughSameReviewLifecycle() {
        val courseId = createNode("COURSE", null, "Course")

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/submit")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("SUBMITTED"))

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/review/start")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/review/approve")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("APPROVED"))

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/publish")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("PUBLISHED"))
    }

    @Test
    fun teacherCanCreatePreviewSubmitAndReviewerCanPublishLesson() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/preview")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.payload.value").value("Lesson"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("SUBMITTED"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/start")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("UNDER_REVIEW"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/feedback")
                    .with(user("reviewer").roles("REVIEWER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("reason" to "Improve the exercise wording."))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("UNDER_REVIEW"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/approve")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("APPROVED"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/publish")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("PUBLISHED"))

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/review-history")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[1].action").value("START_REVIEW"))
            .andExpect(jsonPath("$[2].action").value("FEEDBACK"))
            .andExpect(jsonPath("$[4].action").value("PUBLISH"))

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/archive")
                    .with(user("reviewer").roles("REVIEWER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("ARCHIVED"))
    }

    @Test
    fun teacherCannotPublishDirectly() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/publish")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun validationErrorsBlockSubmission() {
        val courseId = createNode("COURSE", null, "Course")
        val invalidParentId = createNode("EXAMPLE", courseId, "Invalid parent")
        val lessonId = createNode("LESSON", invalidParentId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_PROBLEM_JSON),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.type").value("urn:tildash:problem:validation-failed"))
            .andExpect(jsonPath("$.errors[0].code").value("INVALID_LESSON_PARENT"))
            .andExpect(jsonPath("$.status").value(409))
    }

    @Test
    fun reviewerCanRejectWithAuditableReason() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/start")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/reject")
                    .with(user("reviewer").roles("REVIEWER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("reason" to "Fix the source citation."))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DRAFT"))

        mockMvc
            .perform(
                get("/api/v1/content/$lessonId/review-history")
                    .with(user("teacher").roles("TEACHER"))
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[2].action").value("REJECT"))
            .andExpect(jsonPath("$[2].reason").value("Fix the source citation."))
    }

    @Test
    fun publishedExercisesAreImmutableAtDatabaseBoundary() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")
        publishLesson(lessonId)

        val publishedVersionId =
            jdbcTemplate.queryForObject(
                """
                select pv.id::text
                from tildash.content_published_versions pv
                where pv.content_node_id = ?::uuid
                order by pv.version_no desc
                limit 1
                """.trimIndent(),
                String::class.java,
                lessonId,
            )
        assertNotNull(publishedVersionId)

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                """
                update tildash.content_published_exercises
                set prompt = 'Tampered'
                where published_version_id = ?::uuid
                  and exercise_id = 'exercise-1'
                """.trimIndent(),
                publishedVersionId,
            )
        }

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                """
                delete from tildash.content_published_exercises
                where published_version_id = ?::uuid
                  and exercise_id = 'exercise-1'
                """.trimIndent(),
                publishedVersionId,
            )
        }
    }

    @Test
    fun publishedNodeAndWorkflowHistoryAreImmutableAtDatabaseBoundary() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")
        publishLesson(lessonId)

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "update tildash.content_nodes set position = 9 where id = ?::uuid",
                lessonId,
            )
        }

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                "delete from tildash.content_workflow_events where content_node_id = ?::uuid",
                lessonId,
            )
        }
    }

    @Test
    fun draftCanBeEditedButPublishedContentCannot() {
        val courseId = createNode("COURSE", null, "Course")
        val lessonId = createNode("LESSON", courseId, "Lesson")

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/source-revisions")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            revisionRequest("Updated lesson"),
                        ),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.revision").value(2))

        publishLesson(lessonId)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/source-revisions")
                    .with(user("teacher").roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(revisionRequest("Illegal update"))),
            ).andExpect(status().isConflict)
    }

    private fun publishCourse(courseId: String) {
        mockMvc
            .perform(
                post("/api/v1/content/$courseId/submit")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/review/start")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/review/approve")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$courseId/publish")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)
    }

    private fun publishLesson(lessonId: String) {
        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/submit")
                    .with(user("teacher").roles("TEACHER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/start")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/review/approve")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/v1/content/$lessonId/publish")
                    .with(user("reviewer").roles("REVIEWER")),
            ).andExpect(status().isOk)
    }

    private fun createNode(
        kind: String,
        parentId: String?,
        value: String,
        copyrightStatus: String = "LICENSED",
        includeLicense: Boolean = true,
        seedExercise: Boolean = true,
    ): String {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/content/nodes")
                        .with(user("teacher").roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "kind" to kind,
                                    "parentId" to parentId,
                                    "sourceLocale" to "crh",
                                    "position" to 0,
                                    "payload" to
                                        mapOf(
                                            "type" to "TEXT",
                                            "value" to value,
                                        ),
                                    "provenance" to
                                        mapOf(
                                            "sourceTitle" to "Test source",
                                            "copyrightStatus" to copyrightStatus,
                                            "licenseIdentifier" to if (includeLicense) "CC BY 4.0" else null,
                                            "authorName" to "Teacher",
                                        ),
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val id = response.get("id")?.asText()
        assertNotNull(id)

        if (kind == "LESSON" && seedExercise) {
            mockMvc
                .perform(
                    post("/api/v1/content/$id/exercises")
                        .with(user("teacher").roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "id" to "exercise-1",
                                    "prompt" to "Translate hello.",
                                    "position" to 0,
                                    "expectedAnswers" to listOf("merhaba"),
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
        }

        return id
    }

    private fun revisionRequest(value: String) =
        mapOf(
            "payload" to mapOf("type" to "TEXT", "value" to value),
            "provenance" to
                mapOf(
                    "sourceTitle" to "Test source",
                    "copyrightStatus" to "LICENSED",
                    "licenseIdentifier" to "CC BY 4.0",
                    "authorName" to "Teacher",
                ),
        )
}
