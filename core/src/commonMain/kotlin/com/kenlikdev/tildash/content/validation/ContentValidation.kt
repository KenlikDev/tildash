package com.kenlikdev.tildash.content.validation

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentNode
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.model.CopyrightStatus
import com.kenlikdev.tildash.content.model.LocalizedContentRevision
import com.kenlikdev.tildash.content.model.SourceContentRevision

enum class ValidationSeverity {
    ERROR,
    WARNING,
}

enum class ValidationCode {
    LESSON_NOT_FOUND,
    TARGET_IS_NOT_LESSON,
    DUPLICATE_CONTENT_ID,
    MISSING_PARENT,
    INVALID_ROOT_PARENT,
    INVALID_LESSON_PARENT,
    CONTENT_TREE_CYCLE,
    DUPLICATE_SIBLING_POSITION,
    MISSING_SOURCE_REVISION,
    DUPLICATE_SOURCE_REVISION,
    DUPLICATE_LOCALIZATION_REVISION,
    INVALID_LOCALIZATION_LINEAGE,
    UNKNOWN_COPYRIGHT_STATUS,
    MISSING_LICENSE,
    RESTRICTED_CONTENT_PUBLISHED,
    INVALID_MEDIA_REFERENCE_PAYLOAD,
    INVALID_MEDIA_URI,
    INVALID_MEDIA_TYPE,
    INVALID_CONTENT_PAYLOAD,
    UNKNOWN_EXERCISE_CONTENT,
    DUPLICATE_EXERCISE_ID,
    EMPTY_EXERCISE_PROMPT,
    EMPTY_EXPECTED_ANSWER,
    DUPLICATE_EXPECTED_ANSWER,
    EXERCISE_OPTION_SET_INVALID,
    EXPECTED_ANSWER_NOT_IN_OPTIONS,
}

data class ValidationIssue(
    val code: ValidationCode,
    val severity: ValidationSeverity,
    val message: String,
    val path: String? = null,
)

enum class ReviewOutcome {
    PASS,
    PASS_WITH_WARNINGS,
    FAIL,
}

data class ReviewResult(
    val issues: List<ValidationIssue>,
) {
    val errors: List<ValidationIssue>
        get() = issues.filter { it.severity == ValidationSeverity.ERROR }

    val warnings: List<ValidationIssue>
        get() = issues.filter { it.severity == ValidationSeverity.WARNING }

    val outcome: ReviewOutcome
        get() =
            when {
                errors.isNotEmpty() -> ReviewOutcome.FAIL
                warnings.isNotEmpty() -> ReviewOutcome.PASS_WITH_WARNINGS
                else -> ReviewOutcome.PASS
            }

    val canSubmit: Boolean
        get() = errors.isEmpty()

    val canPublish: Boolean
        get() = outcome == ReviewOutcome.PASS
}

data class ExerciseDefinition(
    val id: String,
    val prompt: String,
    val expectedAnswers: List<String>,
    val options: List<String> = emptyList(),
)

data class LessonValidationInput(
    val lessonId: ContentId,
    val nodes: List<ContentNode>,
    val sourceRevisions: List<SourceContentRevision>,
    val localizationRevisions: List<LocalizedContentRevision> = emptyList(),
    val exercisesByContentId: Map<ContentId, List<ExerciseDefinition>> = emptyMap(),
)

class ContentValidator {
    fun validateLesson(input: LessonValidationInput): ReviewResult {
        val issues = mutableListOf<ValidationIssue>()
        val nodesById = input.nodes.groupBy { it.id }

        if (nodesById[input.lessonId].isNullOrEmpty()) {
            issues += error(
                ValidationCode.LESSON_NOT_FOUND,
                "Lesson '${input.lessonId.value}' is not present in the content tree.",
                "lessonId",
            )
        } else if (nodesById.getValue(input.lessonId).size > 1) {
            issues += error(
                ValidationCode.DUPLICATE_CONTENT_ID,
                "Lesson '${input.lessonId.value}' appears more than once.",
                "lessonId",
            )
        }

        val lesson = nodesById[input.lessonId]?.singleOrNull()
        if (lesson != null && lesson.kind != ContentKind.LESSON) {
            issues += error(
                ValidationCode.TARGET_IS_NOT_LESSON,
                "Validation target '${input.lessonId.value}' must be a lesson node.",
                "lessonId",
            )
        }

        nodesById
            .filterValues { it.size > 1 }
            .keys
            .filter { it != input.lessonId }
            .forEach { id ->
                issues += error(
                    ValidationCode.DUPLICATE_CONTENT_ID,
                    "Content ID '${id.value}' appears more than once.",
                    "nodes[$$id]",
                )
            }

        val nodeIdSet = nodesById.keys
        input.nodes.forEach { node ->
            when {
                node.kind == ContentKind.COURSE && node.parentId != null ->
                    issues += error(
                        ValidationCode.INVALID_ROOT_PARENT,
                        "Course '${node.id.value}' must not have a parent.",
                        "nodes[$${node.id.value}].parentId",
                    )

                node.kind != ContentKind.COURSE && node.parentId == null ->
                    issues += error(
                        ValidationCode.MISSING_PARENT,
                        "Non-course node '${node.id.value}' must have a parent.",
                        "nodes[$${node.id.value}].parentId",
                    )

                node.parentId != null && node.parentId !in nodeIdSet ->
                    issues += error(
                        ValidationCode.MISSING_PARENT,
                        "Parent '${node.parentId.value}' for node '${node.id.value}' is missing.",
                        "nodes[$${node.id.value}].parentId",
                    )
            }
        }

        if (lesson != null) {
            val parent = lesson.parentId?.let { nodesById[it]?.singleOrNull() }
            if (parent != null && parent.kind != ContentKind.COURSE) {
                issues += error(
                    ValidationCode.INVALID_LESSON_PARENT,
                    "Lesson '${lesson.id.value}' must be a direct child of a course.",
                    "lesson.parentId",
                )
            }

            validateTreeCycles(input.nodes, issues)
            validateSiblingPositions(input.nodes, issues)
            validateSourceRevisions(input, issues, nodesById)
            validateLocalizations(input, issues, nodeIdSet, nodesById)
            validateExercises(input, issues, nodeIdSet, lesson.id)
            validatePayloads(input.sourceRevisions, issues, nodesById)
        }

        return ReviewResult(issues)
    }

    private fun validateTreeCycles(
        nodes: List<ContentNode>,
        issues: MutableList<ValidationIssue>,
    ) {
        val parentById = nodes.associate { it.id to it.parentId }
        val reported = mutableSetOf<ContentId>()

        for (node in nodes) {
            val visited = mutableSetOf<ContentId>()
            var current: ContentId? = node.id

            while (current != null) {
                if (!visited.add(current)) {
                    if (reported.add(current)) {
                        issues += error(
                            ValidationCode.CONTENT_TREE_CYCLE,
                            "Content tree contains a parent cycle involving '${current.value}'.",
                            "nodes[$$current]",
                        )
                    }
                    break
                }
                current = parentById[current]
            }
        }
    }

    private fun validateSiblingPositions(
        nodes: List<ContentNode>,
        issues: MutableList<ValidationIssue>,
    ) {
        nodes
            .groupBy { it.parentId }
            .forEach { (parentId, siblings) ->
                siblings
                    .groupBy { it.position }
                    .filterValues { it.size > 1 }
                    .forEach { (position, duplicates) ->
                        val ids = duplicates.joinToString { it.id.value }
                        issues += error(
                            ValidationCode.DUPLICATE_SIBLING_POSITION,
                            "Sibling position $position is used by multiple nodes: $ids.",
                            "nodes[parent=$$parentId]",
                        )
                    }
            }
    }

    private fun validateSourceRevisions(
        input: LessonValidationInput,
        issues: MutableList<ValidationIssue>,
        nodesById: Map<ContentId, List<ContentNode>>,
    ) {
        val revisionsByContent = input.sourceRevisions.groupBy { it.contentId }

        revisionsByContent.forEach { (contentId, revisions) ->
            revisions
                .groupBy { it.revision }
                .filterValues { it.size > 1 }
                .forEach { (revision, duplicates) ->
                    if (duplicates.size > 1) {
                        issues += error(
                            ValidationCode.DUPLICATE_SOURCE_REVISION,
                            "Source revision $revision for '${contentId.value}' is duplicated.",
                            "sourceRevisions[$$contentId]",
                        )
                    }
                }

            val contentNode = nodesById[contentId]?.singleOrNull() ?: return@forEach
            validateProvenance(
                contentNode,
                revisions.maxByOrNull { it.revision } ?: return@forEach,
                issues,
            )
        }

        val descendants = descendantsOf(input.lessonId, nodesById.mapNotNullValuesSingle())
        nodesById.values
            .flatten()
            .filter { it.id in descendants && it.kind != ContentKind.COURSE }
            .forEach { node ->
                if (revisionsByContent[node.id].isNullOrEmpty()) {
                    issues += error(
                        ValidationCode.MISSING_SOURCE_REVISION,
                        "Content node '${node.id.value}' has no source revision.",
                        "sourceRevisions[$${node.id.value}]",
                    )
                }
            }
    }

    private fun validateProvenance(
        node: ContentNode,
        revision: SourceContentRevision,
        issues: MutableList<ValidationIssue>,
    ) {
        val provenance = revision.provenance
        when (provenance.copyrightStatus) {
            CopyrightStatus.UNKNOWN ->
                issues += warning(
                    ValidationCode.UNKNOWN_COPYRIGHT_STATUS,
                    "Copyright status for '${node.id.value}' is unknown.",
                    "sourceRevisions[$${node.id.value}].provenance.copyrightStatus",
                )

            CopyrightStatus.LICENSED,
            CopyrightStatus.PERMISSION_GRANTED ->
                if (provenance.license == null) {
                    issues += error(
                        ValidationCode.MISSING_LICENSE,
                        "Content '${node.id.value}' declares $${provenance.copyrightStatus} but has no license reference.",
                        "sourceRevisions[$${node.id.value}].provenance.license",
                    )
                }

            CopyrightStatus.IN_COPYRIGHT ->
                if (provenance.license == null) {
                    issues +=
                        if (node.state == ContentState.DRAFT || node.state == ContentState.SUBMITTED) {
                            warning(
                                ValidationCode.MISSING_LICENSE,
                                "In-copyright content '${node.id.value}' has no license or permission reference yet.",
                                "sourceRevisions[$${node.id.value}].provenance.license",
                            )
                        } else {
                            error(
                                ValidationCode.MISSING_LICENSE,
                                "In-copyright content '${node.id.value}' cannot pass review without a license or permission reference.",
                                "sourceRevisions[$${node.id.value}].provenance.license",
                            )
                        }
                }

            CopyrightStatus.RESTRICTED ->
                if (node.state == ContentState.PUBLISHED) {
                    issues += error(
                        ValidationCode.RESTRICTED_CONTENT_PUBLISHED,
                        "Restricted content '${node.id.value}' cannot be published.",
                        "nodes[$${node.id.value}].state",
                    )
                }

            CopyrightStatus.PUBLIC_DOMAIN -> Unit
        }
    }

    private fun validateLocalizations(
        input: LessonValidationInput,
        issues: MutableList<ValidationIssue>,
        nodeIdSet: Set<ContentId>,
        nodesById: Map<ContentId, List<ContentNode>>,
    ) {
        val descendants = descendantsOf(input.lessonId, nodesById.mapNotNullValuesSingle())
        val localizationsByKey =
            input.localizationRevisions.groupBy {
                Triple(it.contentId, it.locale, it.revision)
            }

        localizationsByKey
            .filterValues { it.size > 1 }
            .forEach { (key, duplicates) ->
                if (duplicates.size > 1) {
                    issues += error(
                        ValidationCode.DUPLICATE_LOCALIZATION_REVISION,
                        "Localization revision $${key.third} for '${key.first.value}' and locale '${key.second.value}' is duplicated.",
                        "localizationRevisions[$$key]",
                    )
                }
            }

        input.localizationRevisions.forEach { revision ->
            if (revision.contentId !in nodeIdSet || revision.contentId !in descendants) {
                issues += error(
                    ValidationCode.INVALID_LOCALIZATION_LINEAGE,
                    "Localization '${revision.contentId.value}' is outside the validated lesson.",
                    "localizationRevisions[$${revision.contentId.value}]",
                )
                return@forEach
            }

            val sourceExists =
                input.sourceRevisions.any {
                    it.contentId == revision.contentId && it.revision == revision.derivedFromSourceRevision
                }

            if (!sourceExists) {
                issues += error(
                    ValidationCode.INVALID_LOCALIZATION_LINEAGE,
                    "Localization revision $${revision.revision} for '${revision.contentId.value}' derives from missing source revision $${revision.derivedFromSourceRevision}.",
                    "localizationRevisions[$${revision.contentId.value}].derivedFromSourceRevision",
                )
            }
        }
    }

    private fun validateExercises(
        input: LessonValidationInput,
        issues: MutableList<ValidationIssue>,
        nodeIdSet: Set<ContentId>,
        lessonId: ContentId,
    ) {
        val descendants =
            descendantsOf(
                lessonId,
                input.nodes.groupBy { it.id }.mapNotNullValuesSingle(),
            )
        val seenExerciseIds = mutableSetOf<String>()

        input.exercisesByContentId.forEach { (contentId, exercises) ->
            if (contentId !in nodeIdSet || contentId !in descendants) {
                issues += error(
                    ValidationCode.UNKNOWN_EXERCISE_CONTENT,
                    "Exercises are attached to content '${contentId.value}', which is outside the validated lesson.",
                    "exercises[$$contentId]",
                )
            }

            exercises.forEach { exercise ->
                if (!seenExerciseIds.add(exercise.id)) {
                    issues += error(
                        ValidationCode.DUPLICATE_EXERCISE_ID,
                        "Exercise '${exercise.id}' is duplicated.",
                        "exercises[$${contentId}][${exercise.id}]",
                    )
                }

                if (exercise.prompt.isBlank()) {
                    issues += error(
                        ValidationCode.EMPTY_EXERCISE_PROMPT,
                        "Exercise '${exercise.id}' must have a non-blank prompt.",
                        "exercises[$${contentId}][${exercise.id}].prompt",
                    )
                }

                if (exercise.expectedAnswers.isEmpty()) {
                    issues += error(
                        ValidationCode.EMPTY_EXPECTED_ANSWER,
                        "Exercise '${exercise.id}' must define at least one expected answer.",
                        "exercises[$${contentId}][${exercise.id}].expectedAnswers",
                    )
                }

                val normalizedAnswers = exercise.expectedAnswers.map { it.trim().lowercase() }
                if (normalizedAnswers.any { it.isBlank() }) {
                    issues += error(
                        ValidationCode.EMPTY_EXPECTED_ANSWER,
                        "Exercise '${exercise.id}' contains a blank expected answer.",
                        "exercises[$${contentId}][${exercise.id}].expectedAnswers",
                    )
                }

                if (normalizedAnswers.toSet().size != normalizedAnswers.size) {
                    issues += error(
                        ValidationCode.DUPLICATE_EXPECTED_ANSWER,
                        "Exercise '${exercise.id}' contains duplicate expected answers.",
                        "exercises[$${contentId}][${exercise.id}].expectedAnswers",
                    )
                }

                if (exercise.options.isNotEmpty()) {
                    val normalizedOptions = exercise.options.map { it.trim().lowercase() }
                    if (normalizedOptions.size < 2 || normalizedOptions.any { it.isBlank() }) {
                        issues += error(
                            ValidationCode.EXERCISE_OPTION_SET_INVALID,
                            "Exercise '${exercise.id}' must have at least two non-blank options.",
                            "exercises[$${contentId}][${exercise.id}].options",
                        )
                    }

                    if (normalizedOptions.toSet().size != normalizedOptions.size) {
                        issues += error(
                            ValidationCode.EXERCISE_OPTION_SET_INVALID,
                            "Exercise '${exercise.id}' must not contain duplicate options.",
                            "exercises[$${contentId}][${exercise.id}].options",
                        )
                    }

                    if (normalizedAnswers.any { it !in normalizedOptions }) {
                        issues += error(
                            ValidationCode.EXPECTED_ANSWER_NOT_IN_OPTIONS,
                            "Every expected answer for exercise '${exercise.id}' must be present in its option set.",
                            "exercises[$${contentId}][${exercise.id}]",
                        )
                    }
                }
            }
        }
    }

    private fun validatePayloads(
        sourceRevisions: List<SourceContentRevision>,
        issues: MutableList<ValidationIssue>,
        nodesById: Map<ContentId, List<ContentNode>>,
    ) {
        sourceRevisions
            .groupBy { it.contentId }
            .mapValues { (_, revisions) -> revisions.maxByOrNull { it.revision } }
            .forEach { (contentId, revision) ->
                val node = nodesById[contentId]?.singleOrNull() ?: return@forEach
                val payload = revision?.payload ?: return@forEach

                when (node.kind) {
                    ContentKind.COURSE,
                    ContentKind.LESSON,
                    ContentKind.EXAMPLE ->
                        if (payload !is ContentPayload.Text) {
                            issues += error(
                                ValidationCode.INVALID_CONTENT_PAYLOAD,
                                "Content kind '${node.kind}' requires a text payload.",
                                "sourceRevisions[$$contentId].payload",
                            )
                        }

                    ContentKind.VOCABULARY ->
                        if (payload !is ContentPayload.Vocabulary) {
                            issues += error(
                                ValidationCode.INVALID_CONTENT_PAYLOAD,
                                "Vocabulary content requires a vocabulary payload.",
                                "sourceRevisions[$$contentId].payload",
                            )

                    ContentKind.MEDIA_REFERENCE -> {
                        if (payload !is ContentPayload.MediaReference) {
                            issues += error(
                                ValidationCode.INVALID_MEDIA_REFERENCE_PAYLOAD,
                                "Media reference content requires a media-reference payload.",
                                "sourceRevisions[$$contentId].payload",
                            )
                        } else {
                            validateMediaReference(contentId, payload, issues)
                        }
                    }
                }
            }
    }

    private fun validateMediaReference(
        contentId: ContentId,
        payload: ContentPayload.MediaReference,
        issues: MutableList<ValidationIssue>,
    ) {
        if (!MEDIA_URI_PATTERN.matches(payload.uri)) {
            issues += error(
                ValidationCode.INVALID_MEDIA_URI,
                "Media URI '${payload.uri}' must contain a valid URI scheme and must not contain whitespace.",
                "sourceRevisions[$$contentId].payload.uri",
            )
        }

        val mediaType = payload.mediaType
        if (mediaType != null && !MEDIA_TYPE_PATTERN.matches(mediaType)) {
            issues += error(
                ValidationCode.INVALID_MEDIA_TYPE,
                "Media type '${mediaType}' must use type/subtype syntax.",
                "sourceRevisions[$$contentId].payload.mediaType",
            )
        }
    }

    private fun descendantsOf(
        rootId: ContentId,
        nodesById: Map<ContentId, ContentNode>,
    ): Set<ContentId> {
        val childrenByParent = nodesById.values.groupBy { it.parentId }
        val result = mutableSetOf<ContentId>()
        val queue = ArrayDeque<ContentId>()
        queue.add(rootId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!result.add(current)) continue
            childrenByParent[current].orEmpty().forEach { queue.add(it.id) }
        }

        return result
    }

    private fun error(
        code: ValidationCode,
        message: String,
        path: String? = null,
    ) = ValidationIssue(code, ValidationSeverity.ERROR, message, path)

    private fun warning(
        code: ValidationCode,
        message: String,
        path: String? = null,
    ) = ValidationIssue(code, ValidationSeverity.WARNING, message, path)

    private companion object {
        val MEDIA_URI_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*://[^\\s]+$")
        val MEDIA_TYPE_PATTERN = Regex("^[^\\s/]+/[^\\s/]+$")
    }
}

private fun <K, V> Map<K, List<V>>.mapNotNullValuesSingle(): Map<K, V> =
    mapNotNull { (key, values) ->
        values.singleOrNull()?.let { key to it }
    }.toMap()
