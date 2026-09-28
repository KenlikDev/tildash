package com.kenlikdev.tildash.learning

import com.kenlikdev.tildash.content.model.ContentId
import com.kenlikdev.tildash.content.model.ContentKind
import com.kenlikdev.tildash.content.model.ContentNode
import com.kenlikdev.tildash.content.model.ContentPayload
import com.kenlikdev.tildash.content.model.ContentState
import com.kenlikdev.tildash.content.model.ContentVariantType
import com.kenlikdev.tildash.content.model.CopyrightStatus
import com.kenlikdev.tildash.content.model.LanguageTag
import com.kenlikdev.tildash.content.model.LicenseReference
import com.kenlikdev.tildash.content.model.PersonReference
import com.kenlikdev.tildash.content.model.Provenance
import com.kenlikdev.tildash.content.model.PublishedContentVersion
import com.kenlikdev.tildash.content.model.PublishedLocalization
import com.kenlikdev.tildash.content.model.SourceContentRevision
import com.kenlikdev.tildash.content.model.SourceReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class LearningCatalogTest {
    private val createdAt = Instant.parse("2026-09-28T08:00:00Z")
    private val courseA = ContentId("550e8400-e29b-41d4-a716-446655440000")
    private val courseB = ContentId("550e8400-e29b-41d4-a716-446655440010")
    private val lessonA1 = ContentId("550e8400-e29b-41d4-a716-446655440001")
    private val lessonA2 = ContentId("550e8400-e29b-41d4-a716-446655440002")
    private val lessonB1 = ContentId("550e8400-e29b-41d4-a716-446655440011")

    @Test
    fun onlyPublishedSnapshotsAreProjected() {
        val nodes =
            listOf(
                node(courseA, ContentKind.COURSE, null, 0),
                node(lessonA1, ContentKind.LESSON, courseA, 0),
                node(lessonA2, ContentKind.LESSON, courseA, 1),
            )
        val versions =
            listOf(
                version(courseA, 1, "Course A"),
                version(lessonA1, 1, "Lesson A1"),
            )

        val catalog = LearningCatalogProjector.project(nodes, versions)
        val course = catalog.courses.single()
        val lessonIds = course.lessons.map { it.id }

        assertEquals(1, catalog.courses.size)
        assertEquals(courseA, course.id)
        assertEquals(listOf(lessonA1), lessonIds)
    }

    @Test
    fun latestPublishedVersionWins() {
        val node = node(courseA, ContentKind.COURSE, null, 0)
        val versions =
            listOf(
                version(courseA, 1, "Old title"),
                version(courseA, 2, "Current title"),
            )

        val catalog = LearningCatalogProjector.project(listOf(node), versions)
        val course = catalog.courses.single()

        assertEquals("Current title", course.title)
        assertEquals(2, course.publishedVersion)
    }

    @Test
    fun duplicatePublishedVersionFailsExplicitly() {
        assertFailsWith<LearningCatalogProjectionViolation> {
            LearningCatalogProjector.project(
                nodes = listOf(node(courseA, ContentKind.COURSE, null, 0)),
                publishedVersions =
                    listOf(
                        version(courseA, 2, "Current title"),
                        version(courseA, 2, "Duplicate title"),
                    ),
            )
        }
    }

    @Test
    fun coursesAndLessonsUseDeterministicTreeOrdering() {
        val nodes =
            listOf(
                node(courseB, ContentKind.COURSE, null, 1),
                node(courseA, ContentKind.COURSE, null, 0),
                node(lessonB1, ContentKind.LESSON, courseB, 0),
                node(lessonA2, ContentKind.LESSON, courseA, 2),
                node(lessonA1, ContentKind.LESSON, courseA, 0),
            )
        val versions =
            listOf(
                version(courseA, 1, "Course A"),
                version(courseB, 1, "Course B"),
                version(lessonA2, 1, "Lesson A2"),
                version(lessonA1, 1, "Lesson A1"),
                version(lessonB1, 1, "Lesson B1"),
            )

        val catalog = LearningCatalogProjector.project(nodes, versions)
        val courseIds = catalog.courses.map { it.id }
        val firstCourseLessons = catalog.courses.first().lessons
        val firstLessonIds = firstCourseLessons.map { it.id }
        val lastCourseLessons = catalog.courses.last().lessons
        val lastLessonIds = lastCourseLessons.map { it.id }

        assertEquals(listOf(courseA, courseB), courseIds)
        assertEquals(listOf(lessonA1, lessonA2), firstLessonIds)
        assertEquals(listOf(lessonB1), lastLessonIds)
    }

    @Test
    fun publishedLocalizationIsProjectedAsImmutableReadModel() {
        val lesson =
            version(
                lessonA1,
                1,
                "Lesson A1",
                localizations =
                    listOf(
                        PublishedLocalization(
                            locale = LanguageTag("ru"),
                            variant = ContentVariantType.LITERARY,
                            revision = 1,
                            payload = ContentPayload.Text("Урок A1"),
                        ),
                        PublishedLocalization(
                            locale = LanguageTag("crh"),
                            variant = ContentVariantType.LITERARY,
                            revision = 1,
                            payload = ContentPayload.Text("A ders A1"),
                        ),
                    ),
            )

        val catalog =
            LearningCatalogProjector.project(
                nodes =
                    listOf(
                        node(courseA, ContentKind.COURSE, null, 0),
                        node(lessonA1, ContentKind.LESSON, courseA, 0),
                    ),
                publishedVersions =
                    listOf(
                        version(courseA, 1, "Course A"),
                        lesson,
                    ),
            )
        val course = catalog.courses.single()
        val lessonReadModel = course.lessons.single()
        val localizations = lessonReadModel.localizations
        val localizedValues = localizations.map { it.value }

        assertEquals(
            listOf("A ders A1", "Урок A1"),
            localizedValues,
        )
    }

    @Test
    fun missingPublishedNodeFailsExplicitly() {
        assertFailsWith<LearningCatalogProjectionViolation> {
            LearningCatalogProjector.project(
                nodes = listOf(node(courseA, ContentKind.COURSE, null, 0)),
                publishedVersions =
                    listOf(
                        version(courseA, 1, "Course A"),
                        version(lessonA1, 1, "Orphan lesson"),
                    ),
            )
        }
    }

    @Test
    fun duplicateNodeIdsFailExplicitly() {
        val duplicate =
            listOf(
                node(courseA, ContentKind.COURSE, null, 0),
                node(courseA, ContentKind.COURSE, null, 1),
            )

        assertFailsWith<LearningCatalogProjectionViolation> {
            LearningCatalogProjector.project(
                duplicate,
                listOf(version(courseA, 1, "Course A")),
            )
        }
    }

    @Test
    fun publishedLessonWithoutPublishedCourseFailsExplicitly() {
        assertFailsWith<LearningCatalogProjectionViolation> {
            LearningCatalogProjector.project(
                listOf(node(lessonA1, ContentKind.LESSON, courseA, 0)),
                listOf(version(lessonA1, 1, "Lesson A1")),
            )
        }
    }

    @Test
    fun publishedLessonWithNonCourseParentFailsExplicitly() {
        assertFailsWith<LearningCatalogProjectionViolation> {
            LearningCatalogProjector.project(
                listOf(
                    node(courseA, ContentKind.COURSE, null, 0),
                    node(lessonA1, ContentKind.LESSON, courseA, 0),
                    node(lessonA2, ContentKind.LESSON, lessonA1, 0),
                ),
                listOf(
                    version(courseA, 1, "Course A"),
                    version(lessonA1, 1, "Lesson A1"),
                    version(lessonA2, 1, "Lesson A2"),
                ),
            )
        }
    }

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
        state = ContentState.PUBLISHED,
        position = position,
    )

    private fun version(
        contentId: ContentId,
        version: Int,
        title: String,
        localizations: List<PublishedLocalization> = emptyList(),
    ) = PublishedContentVersion(
        contentId = contentId,
        version = version,
        sourceRevision =
            SourceContentRevision(
                contentId = contentId,
                revision = version,
                payload = ContentPayload.Text(title),
                provenance =
                    Provenance(
                        source = SourceReference("Test source"),
                        author = PersonReference("Author"),
                        speaker = null,
                        dialect = LanguageTag("crh"),
                        variant = null,
                        license = LicenseReference("CC BY 4.0"),
                        copyrightStatus = CopyrightStatus.LICENSED,
                        reviewer = null,
                        verifiedAt = null,
                    ),
                createdBy = PersonReference("Author"),
                createdAt = createdAt,
            ),
        localizations = localizations,
        publishedBy = PersonReference("Publisher"),
        publishedAt = createdAt,
    )
}
