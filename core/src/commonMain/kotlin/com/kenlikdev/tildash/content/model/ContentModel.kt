package com.kenlikdev.tildash.content.model

import kotlin.time.Instant

data class ContentId(
    val value: String,
) {
    init {
        require(UUID_PATTERN.matches(value)) {
            "Content ID must be a canonical UUID."
        }
    }

    companion object {
        private val UUID_PATTERN =
            Regex(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            )
    }
}

data class LanguageTag(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Language tag must not be blank."
        }
        require(value.none(Char::isWhitespace)) {
            "Language tag must not contain whitespace."
        }
    }
}

enum class ContentKind {
    COURSE,
    LESSON,
    EXAMPLE,
    VOCABULARY,
    MEDIA_REFERENCE,
}

enum class ContentState {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    PUBLISHED,
    ARCHIVED,
}

enum class ContentVariantType {
    LITERARY,
    COLLOQUIAL,
    REGIONAL,
    DIALECTAL,
    HISTORICAL,
    VARIANT,
}

enum class CopyrightStatus {
    UNKNOWN,
    IN_COPYRIGHT,
    PUBLIC_DOMAIN,
    LICENSED,
    PERMISSION_GRANTED,
    RESTRICTED,
}

sealed interface ContentPayload {
    data class Text(
        val value: String,
    ) : ContentPayload {
        init {
            require(value.isNotBlank()) {
                "Text payload must not be blank."
            }
        }
    }

    data class Vocabulary(
        val term: String,
        val gloss: String,
    ) : ContentPayload {
        init {
            require(term.isNotBlank()) {
                "Vocabulary term must not be blank."
            }
            require(gloss.isNotBlank()) {
                "Vocabulary gloss must not be blank."
            }
        }
    }

    data class MediaReference(
        val uri: String,
        val mediaType: String? = null,
    ) : ContentPayload {
        init {
            require(uri.isNotBlank()) {
                "Media URI must not be blank."
            }
        }
    }
}

data class ContentNode(
    val id: ContentId,
    val kind: ContentKind,
    val parentId: ContentId?,
    val sourceLocale: LanguageTag,
    val state: ContentState,
    val position: Int,
) {
    init {
        require(position >= 0) {
            "Content position must not be negative."
        }

        if (kind == ContentKind.COURSE) {
            require(parentId == null) {
                "A course cannot have a parent content node."
            }
        } else {
            require(parentId != null) {
                "A non-course content node must have a parent content node."
            }
        }

        require(parentId != id) {
            "A content node cannot be its own parent."
        }
    }
}

data class SourceReference(
    val title: String,
    val locator: String? = null,
) {
    init {
        require(title.isNotBlank()) {
            "Source title must not be blank."
        }
    }
}

data class PersonReference(
    val displayName: String,
    val externalId: String? = null,
) {
    init {
        require(displayName.isNotBlank()) {
            "Person display name must not be blank."
        }
    }
}

data class LicenseReference(
    val identifier: String,
    val url: String? = null,
) {
    init {
        require(identifier.isNotBlank()) {
            "License identifier must not be blank."
        }
    }
}

data class Provenance(
    val source: SourceReference,
    val author: PersonReference?,
    val speaker: PersonReference?,
    val dialect: LanguageTag?,
    val variant: ContentVariantType?,
    val license: LicenseReference?,
    val copyrightStatus: CopyrightStatus,
    val reviewer: PersonReference?,
    val verifiedAt: Instant?,
) {
    init {
        require((reviewer == null) == (verifiedAt == null)) {
            "Reviewer and verification time must be provided together."
        }
    }
}

data class SourceContentRevision(
    val contentId: ContentId,
    val revision: Int,
    val payload: ContentPayload,
    val provenance: Provenance,
    val createdBy: PersonReference,
    val createdAt: Instant,
) {
    init {
        require(revision > 0) {
            "Source revision number must be positive."
        }
    }
}

data class LocalizedContentRevision(
    val contentId: ContentId,
    val locale: LanguageTag,
    val revision: Int,
    val variant: ContentVariantType,
    val payload: ContentPayload,
    val derivedFromSourceRevision: Int,
    val state: ContentState,
    val createdBy: PersonReference,
    val createdAt: Instant,
) {
    init {
        require(revision > 0) {
            "Localization revision number must be positive."
        }
        require(derivedFromSourceRevision > 0) {
            "Source revision reference must be positive."
        }
    }
}

data class PublishedLocalization(
    val locale: LanguageTag,
    val variant: ContentVariantType,
    val revision: Int,
    val payload: ContentPayload,
) {
    init {
        require(revision > 0) {
            "Published localization revision must be positive."
        }
    }
}

data class PublishedContentVersion(
    val contentId: ContentId,
    val version: Int,
    val sourceRevision: SourceContentRevision,
    val localizations: List<PublishedLocalization>,
    val publishedBy: PersonReference,
    val publishedAt: Instant,
) {
    init {
        require(version > 0) {
            "Published version must be positive."
        }
    }
}
