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
import kotlin.test.assertTrue
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

        assertEquals(1, catalog.courses.size)
        assertEquals(courseA, catalog.courses.single().id)
        assertEquals(listOf(lessonA1), catalog.courses.single().lessons.map { it.id })
    }

    @Test
    fun latestPublishedVersionWins() {
        val node = node(courseA, ContentKind.COURSE, null, 0)
        val versions =
            listOf(
                version(courseA, 1, "Old title"),
                version(courseA, 2, "Current title"),
                version(courseA, 1, "Duplicate older title"),
            )

        val catalog = LearningCatalogProjector.project(listOf(node), versions)

        assertEquals("Current title", catalog.courses.single().title)
        assertEquals(2, catalog.courses.single().publishedVersion)
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

        assertEquals(listOf(courseA, courseB), catalog.courses.map { it.id })
        assertEquals(listOf(lessonA1, lessonA2), catalog.courses.first().lessons.map { it.id })
        assertEquals(listOf(lessonB1), catalog.courses.last().lessons.map { it.id })
    }

    @Test
    fun publishedLocalizationIsProjectedAsImmutableReadModel() {
        val course = version(
            courseA,
            1,
            "Course A",
            localizations =
                listOf(
                    PublishedLocalization(
                        locale = LanguageTag("ru"),
                        variant = ContentVariantType.LITERARY,
                        revision = 1,
                        payload = ContentPayload.Text("Курс A"),
                    ),
                    PublishedLocalization(
                        locale = LanguageTag("crh"),
                        variant = ContentVariantType.LITERARY,
                        revision = 1,
                        payload = ContentPayload.Text("A kursu"),
                    ),
                ),
        )

        val catalog =
            LearningCatalogProjector.project(
                listOf(node(courseA, ContentKind.COURSE, null, 0)),
                listOf(course),
            )

        assertEquals(listOf("A kursu", "Курс A"), catalog.courses.single().lessons.singleOrNull()?.localizations?.map { it.value }.orEmpty())
            .also { assertTrue(it.isEmpty()) }
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
