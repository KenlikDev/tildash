package com.kenlikdev.tildash.server

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
@Import(PostgresTestConfiguration::class)
class ContentSchemaTests {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun canonicalContentTablesExist() {
        val expectedTables =
            setOf(
                "content_nodes",
                "content_provenance",
                "content_source_revisions",
                "content_localization_revisions",
                "content_published_versions",
                "content_published_localizations",
            )

        val actualTables =
            jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = 'tildash'
                """.trimIndent(),
                String::class.java,
            ).toSet()

        assertEquals(expectedTables, actualTables.intersect(expectedTables))
    }

    @Test
    fun historyTablesRejectUpdateAndDelete() {
        val contentId = UUID.randomUUID()
        val provenanceId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        val localizationRevisionId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val publishedLocalizationId = UUID.randomUUID()
        val publishedAt = "2026-09-27T00:00:00Z"

        jdbcTemplate.update(
            """
            insert into tildash.content_nodes (
                id, kind, parent_id, source_locale, state, position
            )
            values (?, 'COURSE', null, 'crh', 'PUBLISHED', 0)
            """.trimIndent(),
            contentId,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_provenance (
                id, source_title, copyright_status
            )
            values (?, 'Test source', 'LICENSED')
            """.trimIndent(),
            provenanceId,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_source_revisions (
                id, content_node_id, revision_no, payload, provenance_id, created_by
            )
            values (?, ?, 1, '{"type":"text","value":"Course"}'::jsonb, ?, 'test')
            """.trimIndent(),
            revisionId,
            contentId,
            provenanceId,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_localization_revisions (
                id, content_node_id, locale, revision_no, variant_type, payload,
                derived_from_source_revision, state, created_by
            )
            values (?, ?, 'ru', 1, 'LITERARY',
                '{"type":"text","value":"Курс"}'::jsonb, 1, 'PUBLISHED', 'test')
            """.trimIndent(),
            localizationRevisionId,
            contentId,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_published_versions (
                id, content_node_id, version_no, source_revision_id, provenance_id,
                published_by, published_at
            )
            values (?, ?, 1, ?, ?, 'test', ?::timestamptz)
            """.trimIndent(),
            versionId,
            contentId,
            revisionId,
            provenanceId,
            publishedAt,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_published_localizations (
                id, published_version_id, locale, variant_type, revision_no,
                payload, localization_revision_id
            )
            values (?, ?, 'ru', 'LITERARY', 1,
                '{"type":"text","value":"Курс"}'::jsonb, ?)
            """.trimIndent(),
            publishedLocalizationId,
            versionId,
            localizationRevisionId,
        )

        val mutations =
            listOf(
                "update tildash.content_provenance set source_title = 'Changed' where id = '$provenanceId'",
                "update tildash.content_source_revisions set created_by = 'changed' where id = '$revisionId'",
                "update tildash.content_localization_revisions set created_by = 'changed' where id = '$localizationRevisionId'",
                "update tildash.content_published_versions set version_no = 2 where id = '$versionId'",
                "update tildash.content_published_localizations set revision_no = 2 where id = '$publishedLocalizationId'",
            )

        mutations.forEach { statement ->
            assertFailsWith<DataAccessException> {
                jdbcTemplate.execute(statement)
            }
        }
    }

    @Test
    fun publishedVersionsCannotBeUpdatedOrDeleted() {
        val contentId = UUID.randomUUID()
        val provenanceId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val publishedAt = "2026-09-27T00:00:00Z"

        jdbcTemplate.update(
            """
            insert into tildash.content_nodes (
                id,
                kind,
                parent_id,
                source_locale,
                state,
                position
            )
            values (?, 'COURSE', null, 'crh', 'PUBLISHED', 0)
            """.trimIndent(),
            contentId,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_provenance (
                id,
                source_title,
                copyright_status,
                reviewer_name,
                verified_at
            )
            values (?, 'Test source', 'LICENSED', 'Test reviewer', ?::timestamptz)
            """.trimIndent(),
            provenanceId,
            publishedAt,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_source_revisions (
                id,
                content_node_id,
                revision_no,
                payload,
                provenance_id,
                created_by
            )
            values (?, ?, 1, '{"type":"text","value":"Course"}'::jsonb, ?, 'test')
            """.trimIndent(),
            revisionId,
            contentId,
            provenanceId,
        )
        jdbcTemplate.update(
            """
            insert into tildash.content_published_versions (
                id,
                content_node_id,
                version_no,
                source_revision_id,
                provenance_id,
                published_by,
                published_at
            )
            values (?, ?, 1, ?, ?, 'test-reviewer', ?::timestamptz)
            """.trimIndent(),
            versionId,
            contentId,
            revisionId,
            provenanceId,
            publishedAt,
        )

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                """
                update tildash.content_published_versions
                set version_no = 2
                where id = ?
                """.trimIndent(),
                versionId,
            )
        }

        assertFailsWith<DataAccessException> {
            jdbcTemplate.update(
                """
                delete from tildash.content_published_versions
                where id = ?
                """.trimIndent(),
                versionId,
            )
        }
    }
}
