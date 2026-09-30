package com.kenlikdev.tildash.server.learning

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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

data class PublishedLearningCatalogData(
    val nodes: List<ContentNode>,
    val publishedVersions: List<PublishedContentVersion>,
)

interface PublishedLearningCatalogRepository {
    fun load(): PublishedLearningCatalogData
}

@Repository
class JdbcPublishedLearningCatalogRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : PublishedLearningCatalogRepository {
    override fun load(): PublishedLearningCatalogData {
        val nodes =
            jdbc.query(
                """
                select distinct n.id, n.kind, n.parent_id, n.source_locale, n.state, n.position
                from tildash.content_nodes n
                join tildash.content_published_versions pv on pv.content_node_id = n.id
                order by n.id
                """.trimIndent(),
                emptyMap<String, Any>(),
            ) { rs, _ -> mapNode(rs) }

        if (nodes.isEmpty()) {
            return PublishedLearningCatalogData(
                nodes = emptyList(),
                publishedVersions = emptyList(),
            )
        }

        val nodesById = nodes.associateBy { it.id }
        val versions =
            jdbc.query(
                """
                select
                    pv.id as published_version_id,
                    pv.content_node_id,
                    pv.version_no,
                    pv.published_by,
                    pv.published_at,
                    sr.revision_no,
                    sr.payload as source_payload,
                    sr.created_by,
                    sr.created_at,
                    p.source_title,
                    p.source_locator,
                    p.author_name,
                    p.speaker_name,
                    p.dialect,
                    p.variant_type,
                    p.license_identifier,
                    p.license_url,
                    p.copyright_status,
                    p.reviewer_name,
                    p.verified_at
                from tildash.content_published_versions pv
                join tildash.content_source_revisions sr on sr.id = pv.source_revision_id
                join tildash.content_provenance p on p.id = pv.provenance_id
                order by pv.content_node_id, pv.version_no
                """.trimIndent(),
                emptyMap<String, Any>(),
            ) { rs, _ ->
                mapPublishedVersion(rs, nodesById)
            }

        val localizations =
            jdbc.query(
                """
                select
                    pl.published_version_id,
                    n.kind,
                    pl.locale,
                    pl.variant_type,
                    pl.revision_no,
                    pl.payload
                from tildash.content_published_localizations pl
                join tildash.content_published_versions pv on pv.id = pl.published_version_id
                join tildash.content_nodes n on n.id = pv.content_node_id
                order by pl.published_version_id, pl.locale, pl.variant_type, pl.revision_no
                """.trimIndent(),
                emptyMap<String, Any>(),
            ) { rs, _ ->
                mapLocalization(rs)
            }

        val localizationsByVersionId =
            localizations.groupBy(
                keySelector = { row -> row.publishedVersionId },
                valueTransform = { row -> row.localization },
            )

        return PublishedLearningCatalogData(
            nodes = nodes,
            publishedVersions =
                versions.map { version ->
                    version.version.copy(
                        localizations = localizationsByVersionId[version.versionId].orEmpty(),
                    )
                },
        )
    }

    private data class StoredPublishedVersion(
        val versionId: UUID,
        val version: PublishedContentVersion,
    )

    private data class StoredLocalization(
        val publishedVersionId: UUID,
        val localization: PublishedLocalization,
    )

    private fun mapPublishedVersion(
        rs: ResultSet,
        nodesById: Map<ContentId, ContentNode>,
    ): StoredPublishedVersion {
        val contentId = ContentId(rs.getObject("content_node_id", UUID::class.java).toString())
        val nodeKind =
            nodesById[contentId]?.kind
                ?: throw IllegalStateException(
                    "Published content '${contentId.value}' has no content node.",
                )

        val provenance =
            Provenance(
                source =
                    SourceReference(
                        title = rs.getString("source_title"),
                        locator = rs.getString("source_locator"),
                    ),
                author = rs.getString("author_name")?.let(::PersonReference),
                speaker = rs.getString("speaker_name")?.let(::PersonReference),
                dialect = rs.getString("dialect")?.let(::LanguageTag),
                variant = rs.getString("variant_type")?.let(ContentVariantType::valueOf),
                license =
                    rs.getString("license_identifier")?.let {
                        LicenseReference(it, rs.getString("license_url"))
                    },
                copyrightStatus = CopyrightStatus.valueOf(rs.getString("copyright_status")),
                reviewer = rs.getString("reviewer_name")?.let(::PersonReference),
                verifiedAt = rs.getObject("verified_at", OffsetDateTime::class.java)?.toKotlinInstant(),
            )

        val sourceRevision =
            SourceContentRevision(
                contentId = contentId,
                revision = rs.getInt("revision_no"),
                payload = decodePayload(rs.getString("source_payload"), nodeKind),
                provenance = provenance,
                createdBy = PersonReference(rs.getString("created_by")),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toKotlinInstant(),
            )

        return StoredPublishedVersion(
            versionId = rs.getObject("published_version_id", UUID::class.java),
            version =
                PublishedContentVersion(
                    contentId = contentId,
                    version = rs.getInt("version_no"),
                    sourceRevision = sourceRevision,
                    localizations = emptyList(),
                    publishedBy = PersonReference(rs.getString("published_by")),
                    publishedAt = rs.getObject("published_at", OffsetDateTime::class.java).toKotlinInstant(),
                ),
        )
    }

    private fun mapLocalization(rs: ResultSet): StoredLocalization {
        val kind = ContentKind.valueOf(rs.getString("kind"))
        return StoredLocalization(
            publishedVersionId = rs.getObject("published_version_id", UUID::class.java),
            localization =
                PublishedLocalization(
                    locale = LanguageTag(rs.getString("locale")),
                    variant = ContentVariantType.valueOf(rs.getString("variant_type")),
                    revision = rs.getInt("revision_no"),
                    payload = decodePayloadNode(objectMapper.readTree(rs.getString("payload")), kind),
                ),
        )
    }

    private fun mapNode(rs: ResultSet): ContentNode =
        ContentNode(
            id = ContentId(rs.getObject("id", UUID::class.java).toString()),
            kind = ContentKind.valueOf(rs.getString("kind")),
            parentId = rs.getObject("parent_id", UUID::class.java)?.toString()?.let(::ContentId),
            sourceLocale = LanguageTag(rs.getString("source_locale")),
            state = ContentState.valueOf(rs.getString("state")),
            position = rs.getInt("position"),
        )

    private fun decodePayload(
        json: String,
        kind: ContentKind,
    ): ContentPayload = decodePayloadNode(objectMapper.readTree(json), kind)

    private fun decodePayloadNode(
        tree: JsonNode,
        kind: ContentKind,
    ): ContentPayload =
        when (kind) {
            ContentKind.VOCABULARY -> {
                ContentPayload.Vocabulary(
                    term =
                        tree.get("term")?.asText()
                            ?: throw IllegalArgumentException("Vocabulary payload is missing term."),
                    gloss =
                        tree.get("gloss")?.asText()
                            ?: throw IllegalArgumentException("Vocabulary payload is missing gloss."),
                )
            }

            ContentKind.MEDIA_REFERENCE -> {
                ContentPayload.MediaReference(
                    uri =
                        tree.get("uri")?.asText()
                            ?: throw IllegalArgumentException("Media payload is missing uri."),
                    mediaType = tree.get("mediaType")?.asText(),
                )
            }

            else -> {
                ContentPayload.Text(
                    value =
                        tree.get("value")?.asText()
                            ?: throw IllegalArgumentException("Text payload is missing value."),
                )
            }
        }

    private fun OffsetDateTime.toKotlinInstant(): kotlin.time.Instant =
        kotlin.time.Instant.fromEpochMilliseconds(toInstant().toEpochMilli())
}
