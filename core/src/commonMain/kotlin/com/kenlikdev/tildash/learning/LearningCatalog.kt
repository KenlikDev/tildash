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
                                .mapNotNull { lessonId ->
                                    val lessonNode = nodesById.getValue(lessonId)
                                    if (lessonNode.parentId != courseId) {
                                        return@mapNotNull null
                                    }

                                    val lessonVersion = latestPublishedVersions.getValue(lessonId)

                                    LearnerLessonSummary(
                                        id = lessonId,
                                        title = textPayload(lessonVersion),
                                        sourceLocale = lessonNode.sourceLocale,
                                        publishedVersion = lessonVersion.version,
                                        localizations =
                                            lessonVersion.localizations
                                                .map { localization ->
                                                    LearnerLocalizedText(
                                                        locale = localization.locale,
                                                        value = textPayload(localization.payload),
                                                    )
                                                }
                                                .sortedWith(
                                                    compareBy<LearnerLocalizedText> { it.locale.value }
                                                        .thenBy { it.value },
                                                ),
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
            .mapValues { (_, versions) ->
                versions.maxByOrNull { it.version }
                    ?: error("Published version group cannot be empty.")
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

    private fun textPayload(version: PublishedContentVersion): String =
        textPayload(version.sourceRevision.payload)

    private fun textPayload(payload: ContentPayload): String =
        (payload as? ContentPayload.Text)?.value
            ?: throw LearningCatalogProjectionViolation(
                "Learner catalog currently supports text source/localized payloads only.",
            )
}
