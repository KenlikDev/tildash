package com.kenlikdev.tildash.content.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class ContentModelTest {
    private val publishedAt = Instant.parse("2026-09-27T00:00:00Z")
    private val author = PersonReference(displayName = "Course Author")
    private val reviewer = PersonReference(displayName = "Reviewer")

    @Test
    fun crimeanTatarCourseUsesGenericContentTree() {
        val course =
            ContentNode(
                id = ContentId("550e8400-e29b-41d4-a716-446655440000"),
                kind = ContentKind.COURSE,
                parentId = null,
                sourceLocale = LanguageTag("crh"),
                state = ContentState.DRAFT,
                position = 0,
            )
        val lesson =
            ContentNode(
                id = ContentId("550e8400-e29b-41d4-a716-446655440001"),
                kind = ContentKind.LESSON,
                parentId = course.id,
                sourceLocale = LanguageTag("crh"),
                state = ContentState.DRAFT,
                position = 1,
            )
        val example =
            ContentNode(
                id = ContentId("550e8400-e29b-41d4-a716-446655440002"),
                kind = ContentKind.EXAMPLE,
                parentId = lesson.id,
                sourceLocale = LanguageTag("crh"),
                state = ContentState.DRAFT,
                position = 1,
            )

        assertEquals(ContentKind.COURSE, course.kind)
        assertEquals(course.id, lesson.parentId)
        assertEquals(lesson.id, example.parentId)
        assertEquals(LanguageTag("crh"), example.sourceLocale)
    }

    @Test
    fun provenanceIsFirstClassAndVerificationIsConsistent() {
        val provenance =
            Provenance(
                source = SourceReference("Field interview", "archive://source/42"),
                author = author,
                speaker = PersonReference("Speaker"),
                dialect = LanguageTag("crh"),
                variant = ContentVariantType.DIALECTAL,
                license = LicenseReference("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
                copyrightStatus = CopyrightStatus.LICENSED,
                reviewer = reviewer,
                verifiedAt = publishedAt,
            )

        assertEquals("Field interview", provenance.source.title)
        assertEquals(LanguageTag("crh"), provenance.dialect)
        assertEquals(reviewer, provenance.reviewer)
        assertEquals(publishedAt, provenance.verifiedAt)
    }

    @Test
    fun publishedVersionContainsAnImmutableSnapshotShape() {
        val provenance =
            Provenance(
                source = SourceReference("Course source"),
                author = author,
                speaker = null,
                dialect = LanguageTag("crh"),
                variant = ContentVariantType.LITERARY,
                license = LicenseReference("CC BY 4.0"),
                copyrightStatus = CopyrightStatus.LICENSED,
                reviewer = reviewer,
                verifiedAt = publishedAt,
            )
        val sourceRevision =
            SourceContentRevision(
                contentId = ContentId("550e8400-e29b-41d4-a716-446655440000"),
                revision = 3,
                payload = ContentPayload.Text("Merhaba"),
                provenance = provenance,
                createdBy = author,
                createdAt = publishedAt,
            )
        val published =
            PublishedContentVersion(
                contentId = sourceRevision.contentId,
                version = 2,
                sourceRevision = sourceRevision,
                localizations =
                    listOf(
                        PublishedLocalization(
                            locale = LanguageTag("ru"),
                            variant = ContentVariantType.LITERARY,
                            revision = 1,
                            payload = ContentPayload.Text("Здравствуйте"),
                        ),
                    ),
                publishedBy = reviewer,
                publishedAt = publishedAt,
            )

        assertEquals(2, published.version)
        assertEquals(3, published.sourceRevision.revision)
        assertEquals(1, published.localizations.single().revision)
        assertEquals(provenance, published.sourceRevision.provenance)
        assertEquals(ContentPayload.Text("Здравствуйте"), published.localizations.single().payload)
    }

    @Test
    fun invalidContentHierarchyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ContentNode(
                id = ContentId("550e8400-e29b-41d4-a716-446655440003"),
                kind = ContentKind.COURSE,
                parentId = ContentId("550e8400-e29b-41d4-a716-446655440004"),
                sourceLocale = LanguageTag("crh"),
                state = ContentState.DRAFT,
                position = 0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ContentNode(
                id = ContentId("550e8400-e29b-41d4-a716-446655440004"),
                kind = ContentKind.LESSON,
                parentId = null,
                sourceLocale = LanguageTag("crh"),
                state = ContentState.DRAFT,
                position = 0,
            )
        }
    }

    @Test
    fun contentIdRejectsNonUuidValues() {
        assertFailsWith<IllegalArgumentException> {
            ContentId("course-1")
        }
    }

    @Test
    fun languageTagRejectsWhitespace() {
        assertFailsWith<IllegalArgumentException> {
            LanguageTag("crimean tatar")
        }
    }

    @Test
    fun unverifiedProvenanceHasNoReviewer() {
        val provenance =
            Provenance(
                source = SourceReference("Dictionary"),
                author = null,
                speaker = null,
                dialect = null,
                variant = null,
                license = null,
                copyrightStatus = CopyrightStatus.UNKNOWN,
                reviewer = null,
                verifiedAt = null,
            )

        assertNull(provenance.reviewer)
        assertNull(provenance.verifiedAt)
    }
}
