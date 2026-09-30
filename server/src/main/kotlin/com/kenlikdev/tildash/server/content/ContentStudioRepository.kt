package com.kenlikdev.tildash.server.content

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
import com.kenlikdev.tildash.content.model.SourceReference
import com.kenlikdev.tildash.content.validation.ExerciseDefinition
import com.kenlikdev.tildash.content.workflow.ContentWorkflowEvent
import com.kenlikdev.tildash.server.api.content.PayloadRequest
import com.kenlikdev.tildash.server.api.content.PayloadType
import com.kenlikdev.tildash.server.api.content.ProvenanceRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class StoredContentNode(
    val node: ContentNode,
    val updatedAt: OffsetDateTime,
)

data class StoredSourceRevision(
    val id: UUID,
    val revision: SourceContentRevision,
    val provenanceId: UUID,
)

data class StoredPreview(
    val node: ContentNode,
    val revision: Int,
    val payload: JsonNode,
)

data class StoredWorkflowEvent(
    val id: UUID,
    val event: ContentWorkflowEvent,
)

interface ContentStudioRepository {
    fun insertNode(node: ContentNode)

    fun appendSourceRevision(
        contentId: ContentId,
        revision: Int,
        payload: PayloadRequest,
        provenance: ProvenanceRequest,
        createdBy: PersonReference,
        createdAt: OffsetDateTime,
    )

    fun findNode(id: ContentId): StoredContentNode?

    fun nextRevision(contentId: ContentId): Int

    fun updateState(
        contentId: ContentId,
        expected: ContentState,
        next: ContentState,
        updatedAt: OffsetDateTime,
    ): Boolean

    fun insertWorkflowEvent(
        contentId: ContentId,
        event: ContentWorkflowEvent,
    )

    fun validationInput(contentId: ContentId): ValidationData

    fun preview(contentId: ContentId): StoredPreview?

    fun publishSnapshot(
        contentId: ContentId,
        publishedBy: String,
        publishedAt: OffsetDateTime,
    ): Int

    fun reviewHistory(contentId: ContentId): List<StoredWorkflowEvent>
}

data class ValidationData(
    val nodes: List<ContentNode>,
    val sourceRevisions: List<SourceContentRevision>,
    val localizations: List<LocalizedContentRevision>,
    val exercisesByContentId: Map<ContentId, List<ExerciseDefinition>> = emptyMap(),
)

@Repository
class JdbcContentStudioRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : ContentStudioRepository {
    override fun insertNode(node: ContentNode) {
        jdbc.update(
            """
            insert into tildash.content_nodes (
                id, kind, parent_id, source_locale, state, position
            )
            values (
                :id, :kind, :parentId, :sourceLocale, :state, :position
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", UUID.fromString(node.id.value))
                .addValue("kind", node.kind.name)
                .addValue("parentId", node.parentId?.value?.let(UUID::fromString))
                .addValue("sourceLocale", node.sourceLocale.value)
                .addValue("state", node.state.name)
                .addValue("position", node.position),
        )
    }

    override fun appendSourceRevision(
        contentId: ContentId,
        revision: Int,
        payload: PayloadRequest,
        provenance: ProvenanceRequest,
        createdBy: PersonReference,
        createdAt: OffsetDateTime,
    ) {
        val provenanceId = UUID.randomUUID()
        jdbc.update(
            """
            insert into tildash.content_provenance (
                id, source_title, source_locator, author_name, speaker_name,
                dialect, variant_type, license_identifier, license_url,
                copyright_status, reviewer_name, verified_at
            )
            values (
                :id, :sourceTitle, :sourceLocator, :authorName, :speakerName,
                :dialect, :variantType, :licenseIdentifier, :licenseUrl,
                :copyrightStatus, null, null
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", provenanceId)
                .addValue("sourceTitle", provenance.sourceTitle)
                .addValue("sourceLocator", provenance.sourceLocator)
                .addValue("authorName", provenance.authorName ?: createdBy.displayName)
                .addValue("speakerName", provenance.speakerName)
                .addValue("dialect", provenance.dialect)
                .addValue("variantType", provenance.variant)
                .addValue("licenseIdentifier", provenance.licenseIdentifier)
                .addValue("licenseUrl", provenance.licenseUrl)
                .addValue("copyrightStatus", provenance.copyrightStatus.name),
        )

        jdbc.update(
            """
            insert into tildash.content_source_revisions (
                id, content_node_id, revision_no, payload, provenance_id,
                created_by, created_at
            )
            values (
                :id, :contentId, :revision, CAST(:payload AS jsonb), :provenanceId,
                :createdBy, :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("contentId", UUID.fromString(contentId.value))
                .addValue("revision", revision)
                .addValue("payload", encodePayload(payload))
                .addValue("provenanceId", provenanceId)
                .addValue("createdBy", createdBy.displayName)
                .addValue("createdAt", createdAt),
        )
    }

    override fun findNode(id: ContentId): StoredContentNode? =
        jdbc
            .query(
                """
                select id, kind, parent_id, source_locale, state, position, updated_at
                from tildash.content_nodes
                where id = :id
                """.trimIndent(),
                MapSqlParameterSource("id", UUID.fromString(id.value)),
            ) { rs, _ -> mapNode(rs) }
            .firstOrNull()

    override fun nextRevision(contentId: ContentId): Int =
        jdbc.queryForObject(
            """
            select coalesce(max(revision_no), 0) + 1
            from tildash.content_source_revisions
            where content_node_id = :id
            """.trimIndent(),
            MapSqlParameterSource("id", UUID.fromString(contentId.value)),
            Int::class.java,
        ) ?: 1

    override fun updateState(
        contentId: ContentId,
        expected: ContentState,
        next: ContentState,
        updatedAt: OffsetDateTime,
    ): Boolean =
        jdbc.update(
            """
            update tildash.content_nodes
            set state = :next, updated_at = :updatedAt
            where id = :id and state = :expected
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", UUID.fromString(contentId.value))
                .addValue("expected", expected.name)
                .addValue("next", next.name)
                .addValue("updatedAt", updatedAt),
        ) == 1

    override fun insertWorkflowEvent(
        contentId: ContentId,
        event: ContentWorkflowEvent,
    ) {
        jdbc.update(
            """
            insert into tildash.content_workflow_events (
                id, content_node_id, action, from_state, to_state, actor_subject,
                reason, validation_outcome, created_at
            )
            values (
                :id, :contentId, :action, :fromState, :toState, :actorSubject,
                :reason, :validationOutcome, :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("contentId", UUID.fromString(contentId.value))
                .addValue("action", event.action.name)
                .addValue("fromState", event.fromState.name)
                .addValue("toState", event.toState.name)
                .addValue("actorSubject", event.actorSubject)
                .addValue("reason", event.reason)
                .addValue("validationOutcome", event.validationOutcome)
                .addValue("createdAt", event.occurredAt.toJavaOffsetDateTime()),
        )
    }

    override fun validationInput(contentId: ContentId): ValidationData {
        val nodes =
            jdbc.query(
                """
                with recursive ancestors as (
                    select id, kind, parent_id, source_locale, state, position, updated_at, array[id] as path
                    from tildash.content_nodes
                    where id = :rootId
                    union all
                    select parent.id, parent.kind, parent.parent_id, parent.source_locale,
                           parent.state, parent.position, parent.updated_at, ancestors.path || parent.id
                    from tildash.content_nodes parent
                    join ancestors on ancestors.parent_id = parent.id
                    where not parent.id = any(ancestors.path)
                ),
                descendants as (
                    select id, kind, parent_id, source_locale, state, position, updated_at, array[id] as path
                    from tildash.content_nodes
                    where id = :rootId
                    union all
                    select child.id, child.kind, child.parent_id, child.source_locale,
                           child.state, child.position, child.updated_at, parent.path || child.id
                    from tildash.content_nodes child
                    join descendants parent on child.parent_id = parent.id
                    where not child.id = any(parent.path)
                )
                select distinct on (id)
                       id, kind, parent_id, source_locale, state, position, updated_at
                from (
                    select id, kind, parent_id, source_locale, state, position, updated_at from ancestors
                    union all
                    select id, kind, parent_id, source_locale, state, position, updated_at from descendants
                ) tree
                order by id
                """.trimIndent(),
                MapSqlParameterSource("rootId", UUID.fromString(contentId.value)),
            ) { rs, _ -> mapNode(rs).node }

        if (nodes.isEmpty()) return ValidationData(emptyList(), emptyList(), emptyList())

        val ids = nodes.map { UUID.fromString(it.id.value) }
        val params = MapSqlParameterSource("contentIds", ids)

        val revisions =
            jdbc.query(
                """
                select sr.id, sr.content_node_id, sr.revision_no, sr.payload,
                       sr.provenance_id, sr.created_by, sr.created_at,
                       p.source_title, p.source_locator, p.author_name, p.speaker_name,
                       p.dialect, p.variant_type, p.license_identifier, p.license_url,
                       p.copyright_status, p.reviewer_name, p.verified_at
                from tildash.content_source_revisions sr
                join tildash.content_provenance p on p.id = sr.provenance_id
                where sr.content_node_id in (:contentIds)
                order by sr.content_node_id, sr.revision_no
                """.trimIndent(),
                params,
            ) { rs, _ -> mapSourceRevision(rs, nodes.associateBy { it.id }) }

        val localizations =
            jdbc.query(
                """
                select id, content_node_id, locale, revision_no, variant_type, payload,
                       derived_from_source_revision, state, created_by, created_at
                from tildash.content_localization_revisions
                where content_node_id in (:contentIds)
                order by content_node_id, locale, revision_no
                """.trimIndent(),
                params,
            ) { rs, _ -> mapLocalizationRevision(rs) }

        val exercisesByContentId =
            jdbc
                .query(
                    """
                    select content_node_id, prompt, exercise_id, expected_answers
                    from tildash.content_exercise_definitions
                    where content_node_id in (:contentIds)
                    order by content_node_id, position, exercise_id
                    """.trimIndent(),
                    params,
                ) { rs, _ ->
                    val contentId = ContentId(rs.getObject("content_node_id", UUID::class.java).toString())
                    val exerciseId = rs.getString("exercise_id")
                    contentId to
                        ExerciseDefinition(
                            id = exerciseId,
                            prompt = rs.getString("prompt"),
                            expectedAnswers =
                                objectMapper
                                    .readTree(rs.getString("expected_answers"))
                                    .elements()
                                    .asSequence()
                                    .map { it.asText() }
                                    .toList(),
                        )
                }.groupBy({ it.first }, { it.second })

        return ValidationData(
            nodes = nodes,
            sourceRevisions = revisions.map { it.revision },
            localizations = localizations,
            exercisesByContentId = exercisesByContentId,
        )
    }

    override fun preview(contentId: ContentId): StoredPreview? =
        jdbc
            .query(
                """
                select n.id, n.kind, n.parent_id, n.source_locale, n.state, n.position, n.updated_at,
                       sr.revision_no, sr.payload
                from tildash.content_nodes n
                left join lateral (
                    select revision_no, payload
                    from tildash.content_source_revisions
                    where content_node_id = n.id
                    order by revision_no desc
                    limit 1
                ) sr on true
                where n.id = :id
                """.trimIndent(),
                MapSqlParameterSource("id", UUID.fromString(contentId.value)),
            ) { rs, _ ->
                val node = mapNode(rs).node
                val revision = rs.getInt("revision_no")
                val payload = objectMapper.readTree(rs.getString("payload"))
                StoredPreview(node, revision, payload)
            }.firstOrNull()

    override fun publishSnapshot(
        contentId: ContentId,
        publishedBy: String,
        publishedAt: OffsetDateTime,
    ): Int {
        val source =
            jdbc
                .query(
                    """
                    select sr.id as source_revision_id, sr.provenance_id
                    from tildash.content_source_revisions sr
                    where sr.content_node_id = :id
                    order by sr.revision_no desc
                    limit 1
                    """.trimIndent(),
                    MapSqlParameterSource("id", UUID.fromString(contentId.value)),
                ) { rs, _ ->
                    rs.getObject("source_revision_id", UUID::class.java) to
                        rs.getObject("provenance_id", UUID::class.java)
                }.firstOrNull()
                ?: throw IllegalStateException("Content has no source revision: ${contentId.value}")

        val version =
            jdbc.queryForObject(
                """
                select coalesce(max(version_no), 0) + 1
                from tildash.content_published_versions
                where content_node_id = :id
                """.trimIndent(),
                MapSqlParameterSource("id", UUID.fromString(contentId.value)),
                Int::class.java,
            ) ?: 1

        val versionId = UUID.randomUUID()
        jdbc.update(
            """
            insert into tildash.content_published_versions (
                id, content_node_id, version_no, source_revision_id, provenance_id,
                published_by, published_at
            )
            values (
                :id, :contentId, :version, :sourceRevisionId, :provenanceId,
                :publishedBy, :publishedAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", versionId)
                .addValue("contentId", UUID.fromString(contentId.value))
                .addValue("version", version)
                .addValue("sourceRevisionId", source.first)
                .addValue("provenanceId", source.second)
                .addValue("publishedBy", publishedBy)
                .addValue("publishedAt", publishedAt),
        )

        jdbc.query(
            """
            select id, locale, variant_type, revision_no, payload, id as localization_revision_id
            from (
                select lr.*,
                       row_number() over (
                           partition by lr.locale, lr.variant_type
                           order by lr.revision_no desc
                       ) as rn
                from tildash.content_localization_revisions lr
                where lr.content_node_id = :id
            ) latest
            where rn = 1
            """.trimIndent(),
            MapSqlParameterSource("id", UUID.fromString(contentId.value)),
        ) { rs, _ ->
            jdbc.update(
                """
                insert into tildash.content_published_localizations (
                    id, published_version_id, locale, variant_type, revision_no,
                    payload, localization_revision_id
                )
                values (
                    :id, :publishedVersionId, :locale, :variantType, :revision,
                    CAST(:payload AS jsonb), :localizationRevisionId
                )
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("publishedVersionId", versionId)
                    .addValue("locale", rs.getString("locale"))
                    .addValue("variantType", rs.getString("variant_type"))
                    .addValue("revision", rs.getInt("revision_no"))
                    .addValue("payload", rs.getString("payload"))
                    .addValue("localizationRevisionId", rs.getObject("localization_revision_id", UUID::class.java)),
            )
        }

        return version
    }

    override fun reviewHistory(contentId: ContentId): List<StoredWorkflowEvent> =
        jdbc.query(
            """
            select id, action, from_state, to_state, actor_subject, reason,
                   validation_outcome, created_at
            from tildash.content_workflow_events
            where content_node_id = :id
            order by created_at, id
            """.trimIndent(),
            MapSqlParameterSource("id", UUID.fromString(contentId.value)),
        ) { rs, _ ->
            StoredWorkflowEvent(
                id = rs.getObject("id", UUID::class.java),
                event =
                    ContentWorkflowEvent(
                        action =
                            com.kenlikdev.tildash.content.workflow.ContentWorkflowAction.valueOf(
                                rs.getString("action"),
                            ),
                        actorSubject = rs.getString("actor_subject"),
                        fromState = ContentState.valueOf(rs.getString("from_state")),
                        toState = ContentState.valueOf(rs.getString("to_state")),
                        reason = rs.getString("reason"),
                        validationOutcome = rs.getString("validation_outcome"),
                        occurredAt = rs.getTimestamp("created_at").toKotlinInstant(),
                    ),
            )
        }

    private fun mapNode(rs: ResultSet): StoredContentNode {
        val id = ContentId(rs.getObject("id", UUID::class.java).toString())
        return StoredContentNode(
            node =
                ContentNode(
                    id = id,
                    kind = ContentKind.valueOf(rs.getString("kind")),
                    parentId = rs.getObject("parent_id", UUID::class.java)?.toString()?.let(::ContentId),
                    sourceLocale = LanguageTag(rs.getString("source_locale")),
                    state = ContentState.valueOf(rs.getString("state")),
                    position = rs.getInt("position"),
                ),
            updatedAt =
                rs.getObject("updated_at", OffsetDateTime::class.java)
                    ?: rs.getTimestamp("updated_at").toOffsetDateTimeUtc(),
        )
    }

    private fun mapSourceRevision(
        rs: ResultSet,
        nodesById: Map<ContentId, ContentNode>,
    ): StoredSourceRevision {
        val contentId = ContentId(rs.getObject("content_node_id", UUID::class.java).toString())
        val nodeKind = nodesById[contentId]?.kind
        val provenance =
            Provenance(
                source = SourceReference(rs.getString("source_title"), rs.getString("source_locator")),
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
                verifiedAt = rs.getTimestamp("verified_at")?.toKotlinInstant(),
            )
        val revision =
            SourceContentRevision(
                contentId = contentId,
                revision = rs.getInt("revision_no"),
                payload = decodePayload(rs.getString("payload"), nodeKind),
                provenance = provenance,
                createdBy = PersonReference(rs.getString("created_by")),
                createdAt = rs.getTimestamp("created_at").toKotlinInstant(),
            )
        return StoredSourceRevision(
            id = rs.getObject("id", UUID::class.java),
            revision = revision,
            provenanceId = rs.getObject("provenance_id", UUID::class.java),
        )
    }

    private fun mapLocalizationRevision(rs: ResultSet): LocalizedContentRevision =
        LocalizedContentRevision(
            contentId = ContentId(rs.getObject("content_node_id", UUID::class.java).toString()),
            locale = LanguageTag(rs.getString("locale")),
            revision = rs.getInt("revision_no"),
            variant = ContentVariantType.valueOf(rs.getString("variant_type")),
            payload = decodePayload(rs.getString("payload"), null),
            derivedFromSourceRevision = rs.getInt("derived_from_source_revision"),
            state = ContentState.valueOf(rs.getString("state")),
            createdBy = PersonReference(rs.getString("created_by")),
            createdAt = rs.getTimestamp("created_at").toKotlinInstant(),
        )

    private fun encodePayload(request: PayloadRequest): String =
        when (request.type) {
            PayloadType.TEXT -> {
                objectMapper.writeValueAsString(mapOf("value" to requireNotNull(request.value)))
            }

            PayloadType.VOCABULARY -> {
                objectMapper.writeValueAsString(
                    mapOf(
                        "term" to requireNotNull(request.term),
                        "gloss" to requireNotNull(request.gloss),
                    ),
                )
            }

            PayloadType.MEDIA_REFERENCE -> {
                objectMapper.writeValueAsString(
                    mapOf(
                        "uri" to requireNotNull(request.uri),
                        "mediaType" to request.mediaType,
                    ),
                )
            }
        }

    private fun decodePayload(
        json: String,
        kind: ContentKind?,
    ): ContentPayload {
        val tree = objectMapper.readTree(json)
        return when (kind) {
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
    }
}

private fun kotlin.time.Instant.toJavaOffsetDateTime(): OffsetDateTime =
    java.time.Instant
        .ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
        .atOffset(ZoneOffset.UTC)

private fun Timestamp.toKotlinInstant() = kotlin.time.Instant.fromEpochMilliseconds(toInstant().toEpochMilli())

private fun Timestamp.toOffsetDateTimeUtc() = toInstant().atOffset(ZoneOffset.UTC)
