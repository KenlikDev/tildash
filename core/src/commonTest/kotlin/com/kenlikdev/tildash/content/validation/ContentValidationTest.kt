package com.kenlikdev.tildash.content.validation

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentNode
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.model.ContentVariantType
import com.kenlikdev.tildash.content.model.CopyrightStatus
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.content.model.LicenseReference
import com.kenlikdev.tildash.content.model.LocalizedContentRevision
import com.kenlikdev.tildash.content.model.PersonReference
import com.kenlikdev.tildash.content.model.Provenance
import com.kenlikdev.tildash.content.model.SourceContentRevision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ContentValidationTest {
    private val validator = ContentValidator()
    private val createdAt = Instant.parse("2026-09-27T00:00:00Z")
    private val courseId = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val lessonId = ContentId("550e8400-e29b-41d4-a716-446655440001")
    private val exampleId = ContentId("550e8400-e29b-41d4-a716-446655440002")
    private val vocabularyId = ContentId("550e8400-e29b-41d4-a716-446655440003")
    private val mediaId = ContentId("550e8400-e29b-41d4-a716-446655440004")
    private val author = PersonReference("Author")

    @Test
    fun validLessonPassesAndWarningsAreAbsent() {
        val result = validator.validateLesson(validInput())

        assertEquals(ReviewOutcome.PASS, result.outcome)
        assertTrue(result.issues.isEmpty())
        assertTrue(result.canSubmit)
        assertTrue(result.canPublish)
    }

    @Test
    fun unknownCopyrightStatusProducesExplicitWarning() {
        val input =
            validInput(
                sourceRevisions =
                    listOf(
                        sourceRevision(courseId, 1, CopyrightStatus.UNKNOWN, ContentPayload.Text("Course")),
                        sourceRevision(lessonId, 1, CopyrightStatus.LICENSED, ContentPayload.Text("Lesson")),
                        sourceRevision(exampleId, 1, CopyrightStatus.LICENSED, ContentPayload.Text("Example")),
                        sourceRevision(vocabularyId, 1, CopyrightStatus.LICENSED, ContentPayload.Vocabulary("merhaba", "hello")),
                        sourceRevision(
                            mediaId,
                            1,
                            CopyrightStatus.LICENSED,
                            ContentPayload.MediaReference("https://example.com/audio.mp3", "audio/mpeg"),
                        ),
                    ),
            )

        val result = validator.validateLesson(input)

        assertEquals(ReviewOutcome.PASS_WITH_WARNINGS, result.outcome)
        assertEquals(1, result.warnings.size)
        assertTrue(result.canSubmit)
        assertFalse(result.canPublish)
        assertEquals(ValidationCode.UNKNOWN_COPYRIGHT_STATUS, result.warnings.single().code)
    }

    @Test
    fun invalidLessonParentIsRejected() {
        val nodes =
            listOf(
                node(courseId, ContentKind.COURSE, null, 0),
                node(exampleId, ContentKind.EXAMPLE, courseId, 0),
                node(lessonId, ContentKind.LESSON, exampleId, 0),
            )

        val result = validator.validateLesson(validInput(nodes = nodes))

        assertEquals(ReviewOutcome.FAIL, result.outcome)
        assertHasCode(result, ValidationCode.INVALID_LESSON_PARENT)
    }

    @Test
    fun contentTreeCycleIsRejected() {
        val nodes =
            listOf(
                node(courseId, ContentKind.COURSE, null, 0),
                node(lessonId, ContentKind.LESSON, exampleId, 0),
                node(exampleId, ContentKind.EXAMPLE, lessonId, 1),
            )

        val result = validator.validateLesson(validInput(nodes = nodes))

        assertHasCode(result, ValidationCode.CONTENT_TREE_CYCLE)
    }

    @Test
    fun duplicateContentIdIsReportedWithoutThrowing() {
        val duplicateExample =
            node(exampleId, ContentKind.EXAMPLE, lessonId, 4)

        val result =
            validator.validateLesson(
                validInput(
                    nodes = validInput().nodes + duplicateExample,
                ),
            )

        assertHasCode(result, ValidationCode.DUPLICATE_CONTENT_ID)
    }

    @Test
    fun duplicateSiblingPositionIsRejected() {
        val secondExampleId = ContentId("550e8400-e29b-41d4-a716-446655440005")
        val nodes =
            validInput().nodes +
                node(secondExampleId, ContentKind.EXAMPLE, lessonId, 1)

        val revisions =
            validInput().sourceRevisions +
                sourceRevision(
                    secondExampleId,
                    1,
                    CopyrightStatus.LICENSED,
                    ContentPayload.Text("Second example"),
                )

        val result =
            validator.validateLesson(
                validInput(
                    nodes = nodes,
                    sourceRevisions = revisions,
                ),
            )

        assertHasCode(result, ValidationCode.DUPLICATE_SIBLING_POSITION)
    }

    @Test
    fun missingSourceRevisionIsRejected() {
        val result =
            validator.validateLesson(
                validInput(
                    sourceRevisions = validInput().sourceRevisions.filterNot { it.contentId == exampleId },
                ),
            )

        assertHasCode(result, ValidationCode.MISSING_SOURCE_REVISION)
    }

    @Test
    fun duplicateSourceRevisionIsRejected() {
        val revision = sourceRevision(lessonId, 1, CopyrightStatus.LICENSED, ContentPayload.Text("Lesson"))
        val result =
            validator.validateLesson(
                validInput(sourceRevisions = validInput().sourceRevisions + revision),
            )

        assertHasCode(result, ValidationCode.DUPLICATE_SOURCE_REVISION)
    }

    @Test
    fun invalidLocalizationLineageIsRejected() {
        val localization =
            LocalizedContentRevision(
                contentId = lessonId,
                locale = LanguageTag("ru"),
                revision = 1,
                variant = ContentVariantType.LITERARY,
                payload = ContentPayload.Text("Урок"),
                derivedFromSourceRevision = 99,
                state = ContentState.DRAFT,
                createdBy = author,
                createdAt = createdAt,
            )

        val result =
            validator.validateLesson(
                validInput(localizationRevisions = listOf(localization)),
            )

        assertHasCode(result, ValidationCode.INVALID_LOCALIZATION_LINEAGE)
    }

    @Test
    fun missingLicenseBlocksApprovedInCopyrightContent() {
        val revisions =
            validInput().sourceRevisions.map { revision ->
                if (revision.contentId == lessonId) {
                    revision.copy(
                        provenance =
                            revision.provenance.copy(
                                copyrightStatus = CopyrightStatus.IN_COPYRIGHT,
                                license = null,
                            ),
                    )
                } else {
                    revision
                }
            }

        val result = validator.validateLesson(validInput(sourceRevisions = revisions))

        assertEquals(ReviewOutcome.FAIL, result.outcome)
        assertFalse(result.canSubmit)
        assertFalse(result.canPublish)
        assertHasCode(result, ValidationCode.MISSING_LICENSE)
    }

    @Test
    fun restrictedPublishedContentIsRejected() {
        val nodes =
            validInput().nodes.map { node ->
                if (node.id == mediaId) {
                    node.copy(state = ContentState.PUBLISHED)
                } else {
                    node
                }
            }
        val revisions =
            validInput().sourceRevisions.map { revision ->
                if (revision.contentId == mediaId) {
                    revision.copy(
                        provenance =
                            revision.provenance.copy(
                                copyrightStatus = CopyrightStatus.RESTRICTED,
                            ),
                    )
                } else {
                    revision
                }
            }

        val result = validator.validateLesson(validInput(nodes = nodes, sourceRevisions = revisions))

        assertHasCode(result, ValidationCode.RESTRICTED_CONTENT_PUBLISHED)
    }

    @Test
    fun payloadKindMustMatchContentKind() {
        val revisions =
            validInput().sourceRevisions.map { revision ->
                if (revision.contentId == vocabularyId) {
                    revision.copy(payload = ContentPayload.Text("wrong payload"))
                } else {
                    revision
                }
            }

        val result = validator.validateLesson(validInput(sourceRevisions = revisions))

        assertHasCode(result, ValidationCode.INVALID_CONTENT_PAYLOAD)
    }

    @Test
    fun mediaReferenceRequiresValidUriAndMediaType() {
        val revisions =
            validInput().sourceRevisions.map { revision ->
                if (revision.contentId == mediaId) {
                    revision.copy(
                        payload =
                            ContentPayload.MediaReference(
                                uri = "not a uri",
                                mediaType = "audio",
                            ),
                    )
                } else {
                    revision
                }
            }

        val result = validator.validateLesson(validInput(sourceRevisions = revisions))

        assertHasCode(result, ValidationCode.INVALID_MEDIA_URI)
        assertHasCode(result, ValidationCode.INVALID_MEDIA_TYPE)
    }

    @Test
    fun exerciseMustHaveConsistentAnswersAndOptions() {
        val exercise =
            ExerciseDefinition(
                id = "exercise-1",
                prompt = "Translate",
                expectedAnswers = listOf("Hello", " hello "),
                options = listOf("Hello", "Goodbye"),
            )

        val result =
            validator.validateLesson(
                validInput(
                    exercisesByContentId = mapOf(lessonId to listOf(exercise)),
                ),
            )

        assertHasCode(result, ValidationCode.DUPLICATE_EXPECTED_ANSWER)
    }

    @Test
    fun exerciseMustContainExpectedAnswerInOptions() {
        val exercise =
            ExerciseDefinition(
                id = "exercise-1",
                prompt = "Choose",
                expectedAnswers = listOf("Hello"),
                options = listOf("Goodbye", "Thanks"),
            )

        val result =
            validator.validateLesson(
                validInput(
                    exercisesByContentId = mapOf(lessonId to listOf(exercise)),
                ),
            )

        assertHasCode(result, ValidationCode.EXPECTED_ANSWER_NOT_IN_OPTIONS)
    }

    @Test
    fun exercisesOutsideLessonAreRejected() {
        val exercise =
            ExerciseDefinition(
                id = "exercise-1",
                prompt = "Choose",
                expectedAnswers = listOf("Hello"),
            )

        val result =
            validator.validateLesson(
                validInput(
                    exercisesByContentId = mapOf(courseId to listOf(exercise)),
                ),
            )

        assertHasCode(result, ValidationCode.UNKNOWN_EXERCISE_CONTENT)
    }

    @Test
    fun duplicateExerciseIdsAreRejected() {
        val first =
            ExerciseDefinition(
                id = "exercise-1",
                prompt = "First",
                expectedAnswers = listOf("Hello"),
            )
        val second =
            ExerciseDefinition(
                id = "exercise-1",
                prompt = "Second",
                expectedAnswers = listOf("Hi"),
            )

        val result =
            validator.validateLesson(
                validInput(
                    exercisesByContentId =
                        mapOf(
                            exampleId to listOf(first),
                            vocabularyId to listOf(second),
                        ),
                ),
            )

        assertHasCode(result, ValidationCode.DUPLICATE_EXERCISE_ID)
    }

    private fun validInput(
        nodes: List<ContentNode> = defaultNodes(),
        sourceRevisions: List<SourceContentRevision> = defaultRevisions(),
        localizationRevisions: List<LocalizedContentRevision> = emptyList(),
        exercisesByContentId: Map<ContentId, List<ExerciseDefinition>> = emptyMap(),
    ) = LessonValidationInput(
        lessonId = lessonId,
        nodes = nodes,
        sourceRevisions = sourceRevisions,
        localizationRevisions = localizationRevisions,
        exercisesByContentId = exercisesByContentId,
    )

    private fun defaultNodes() =
        listOf(
            node(courseId, ContentKind.COURSE, null, 0),
            node(lessonId, ContentKind.LESSON, courseId, 0),
            node(exampleId, ContentKind.EXAMPLE, lessonId, 1),
            node(vocabularyId, ContentKind.VOCABULARY, lessonId, 2),
            node(mediaId, ContentKind.MEDIA_REFERENCE, lessonId, 3),
        )

    private fun defaultRevisions() =
        listOf(
            sourceRevision(courseId, 1, CopyrightStatus.LICENSED, ContentPayload.Text("Course")),
            sourceRevision(lessonId, 1, CopyrightStatus.LICENSED, ContentPayload.Text("Lesson")),
            sourceRevision(exampleId, 1, CopyrightStatus.LICENSED, ContentPayload.Text("Example")),
            sourceRevision(
                vocabularyId,
                1,
                CopyrightStatus.LICENSED,
                ContentPayload.Vocabulary("merhaba", "hello"),
            ),
            sourceRevision(
                mediaId,
                1,
                CopyrightStatus.LICENSED,
                ContentPayload.MediaReference("https://example.com/audio.mp3", "audio/mpeg"),
            ),
        )

    private fun node(
        id: ContentId,
        kind: ContentKind,
        parentId: ContentId?,
        position: Int,
    ) = ContentNode(
        id = id,
        kind = kind,
        parentId = parentId,
        sourceLocale = LanguageTag("crh"),
        state = ContentState.DRAFT,
        position = position,
    )

    private fun sourceRevision(
        contentId: ContentId,
        revision: Int,
        copyrightStatus: CopyrightStatus,
        payload: ContentPayload,
    ) = SourceContentRevision(
        contentId = contentId,
        revision = revision,
        payload = payload,
        provenance =
            Provenance(
                source = SourceReference("Source for ${contentId.value}"),
                author = author,
                speaker = null,
                dialect = LanguageTag("crh"),
                variant = null,
                license = LicenseReference("CC BY 4.0"),
                copyrightStatus = copyrightStatus,
                reviewer = null,
                verifiedAt = null,
            ),
        createdBy = author,
        createdAt = createdAt,
    )

    private fun assertHasCode(
        result: ReviewResult,
        code: ValidationCode,
    ) {
        assertTrue(
            result.issues.any { it.code == code },
            "Expected validation code $$code but got $${result.issues.map { it.code }}",
        )
    }
}
