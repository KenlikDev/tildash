package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentNode
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.content.model.PublishedContentVersion

data class LearnerLocalizedText(
    val locale: LanguageTag,
    val value: String,
)

data class LearnerLessonSummary(
    val id: ContentId,
    val title: String,
    val sourceLocale: LanguageTag,
    val publishedVersion: Int,
    val localizations: List<LearnerLocalizedText>,
)

data class LearnerCourseSummary(
    val id: ContentId,
    val title: String,
    val sourceLocale: LanguageTag,
    val publishedVersion: Int,
    val lessons: List<LearnerLessonSummary>,
)

data class LearnerCourseCatalog(
    val courses: List<LearnerCourseSummary>,
)

class LearningCatalogProjectionViolation(
    message: String,
) : IllegalArgumentException(message)

object LearningCatalogProjector {
    fun project(
        nodes: List<ContentNode>,
        publishedVersions: List<PublishedContentVersion>,
    ): LearnerCourseCatalog {
        val nodesById = indexNodes(nodes)
        val latestPublishedVersions = latestVersions(publishedVersions)
        validatePublishedNodeReferences(nodesById, latestPublishedVersions.keys)

        val publishedCourseIds =
            latestPublishedVersions.keys
                .filter { id -> nodesById.getValue(id).kind == ContentKind.COURSE }
                .toSet()

        val publishedLessonIds =
            latestPublishedVersions.keys
                .filter { id -> nodesById.getValue(id).kind == ContentKind.LESSON }
                .toSet()

        validatePublishedHierarchy(
            nodesById = nodesById,
            publishedCourseIds = publishedCourseIds,
            publishedLessonIds = publishedLessonIds,
        )

        val courses =
            publishedCourseIds
                .map { courseId ->
                    val courseNode = nodesById.getValue(courseId)
                    val courseVersion = latestPublishedVersions.getValue(courseId)

                    LearnerCourseSummary(
                        id = courseId,
                        title = textPayload(courseVersion),
                        sourceLocale = courseNode.sourceLocale,
                        publishedVersion = courseVersion.version,
                        lessons =
                            publishedLessonIds
                                .filter { lessonId -> nodesById.getValue(lessonId).parentId == courseId }
                                .map { lessonId ->
                                    val lessonNode = nodesById.getValue(lessonId)
                                    val lessonVersion = latestPublishedVersions.getValue(lessonId)

                                    LearnerLessonSummary(
                                        id = lessonId,
                                        title = textPayload(lessonVersion),
                                        sourceLocale = lessonNode.sourceLocale,
                                        publishedVersion = lessonVersion.version,
                                        localizations = projectLocalizations(lessonVersion),
                                    )
                                }
                                .sortedWith(
                                    compareBy<LearnerLessonSummary> {
                                        nodesById.getValue(it.id).position
                                    }.thenBy { it.id.value },
                                ),
                    )
                }
                .sortedWith(
                    compareBy<LearnerCourseSummary> {
                        nodesById.getValue(it.id).position
                    }.thenBy { it.id.value },
                )

        return LearnerCourseCatalog(courses)
    }

    private fun indexNodes(nodes: List<ContentNode>): Map<ContentId, ContentNode> {
        val duplicates =
            nodes
                .groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys

        if (duplicates.isNotEmpty()) {
            val ids = duplicates.joinToString { it.value }
            throw LearningCatalogProjectionViolation(
                "Duplicate content IDs cannot be projected into a learner catalog: " + ids + ".",
            )
        }

        return nodes.associateBy { it.id }
    }

    private fun latestVersions(
        publishedVersions: List<PublishedContentVersion>,
    ): Map<ContentId, PublishedContentVersion> =
        publishedVersions
            .groupBy { it.contentId }
            .mapValues { (contentId, versions) ->
                val duplicatedVersions =
                    versions
                        .groupingBy { it.version }
                        .eachCount()
                        .filterValues { it > 1 }
                        .keys

                if (duplicatedVersions.isNotEmpty()) {
                    val versionsText = duplicatedVersions.sorted().joinToString()
                    throw LearningCatalogProjectionViolation(
                        "Published content '" + contentId.value + "' has duplicate version numbers: " + versionsText + ".",
                    )
                }

                versions.maxByOrNull { it.version }
                    ?: error("Published version group cannot be empty.")
            }

    private fun validatePublishedNodeReferences(
        nodesById: Map<ContentId, ContentNode>,
        publishedContentIds: Set<ContentId>,
    ) {
        publishedContentIds
            .filterNot { it in nodesById }
            .forEach { contentId ->
                throw LearningCatalogProjectionViolation(
                    "Published content '" + contentId.value + "' has no content node.",
                )
            }
    }

    private fun validatePublishedHierarchy(
        nodesById: Map<ContentId, ContentNode>,
        publishedCourseIds: Set<ContentId>,
        publishedLessonIds: Set<ContentId>,
    ) {
        publishedCourseIds.forEach { courseId ->
            val node =
                nodesById[courseId]
                    ?: throw LearningCatalogProjectionViolation(
                        "Published course '" + courseId.value + "' has no content node.",
                    )

            if (node.kind != ContentKind.COURSE || node.parentId != null) {
                throw LearningCatalogProjectionViolation(
                    "Published course '" + courseId.value + "' has an invalid course hierarchy.",
                )
            }
        }

        publishedLessonIds.forEach { lessonId ->
            val node =
                nodesById[lessonId]
                    ?: throw LearningCatalogProjectionViolation(
                        "Published lesson '" + lessonId.value + "' has no content node.",
                    )
            val parentId = node.parentId

            if (node.kind != ContentKind.LESSON || parentId == null) {
                throw LearningCatalogProjectionViolation(
                    "Published lesson '" + lessonId.value + "' has an invalid lesson hierarchy.",
                )
            }

            if (parentId !in publishedCourseIds) {
                throw LearningCatalogProjectionViolation(
                    "Published lesson '" + lessonId.value + "' must belong to a published course.",
                )
            }
        }
    }

    private fun projectLocalizations(
        version: PublishedContentVersion,
    ): List<LearnerLocalizedText> {
        val duplicateKeys =
            version.localizations
                .groupingBy { Triple(it.locale, it.variant, it.revision) }
                .eachCount()
                .filterValues { it > 1 }
                .keys

        if (duplicateKeys.isNotEmpty()) {
            throw LearningCatalogProjectionViolation(
                "Published content '" + version.contentId.value + "' has duplicate localization revisions.",
            )
        }

        return version.localizations
            .map { localization ->
                LearnerLocalizedText(
                    locale = localization.locale,
                    value = textPayload(localization.payload),
                )
            }
            .sortedWith(
                compareBy<LearnerLocalizedText> { it.locale.value }
                    .thenBy { it.value },
            )
    }

    private fun textPayload(version: PublishedContentVersion): String =
        textPayload(version.sourceRevision.payload)

    private fun textPayload(payload: ContentPayload): String =
        (payload as? ContentPayload.Text)?.value
            ?: throw LearningCatalogProjectionViolation(
                "Learner catalog currently supports text source/localized payloads only.",
            )
}
