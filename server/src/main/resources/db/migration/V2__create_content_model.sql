CREATE TABLE tildash.content_nodes (
    id UUID PRIMARY KEY,
    kind VARCHAR(32) NOT NULL CHECK (
        kind IN ('COURSE', 'LESSON', 'EXAMPLE', 'VOCABULARY', 'MEDIA_REFERENCE')
    ),
    parent_id UUID REFERENCES tildash.content_nodes(id),
    source_locale VARCHAR(35) NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (
        state IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')
    ),
    position INTEGER NOT NULL CHECK (position >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (kind = 'COURSE' AND parent_id IS NULL)
        OR (kind <> 'COURSE' AND parent_id IS NOT NULL)
    ),
    CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE TABLE tildash.content_provenance (
    id UUID PRIMARY KEY,
    source_title TEXT NOT NULL,
    source_locator TEXT,
    author_name TEXT,
    speaker_name TEXT,
    dialect VARCHAR(35),
    variant_type VARCHAR(32) CHECK (
        variant_type IS NULL
        OR variant_type IN ('LITERARY', 'COLLOQUIAL', 'REGIONAL', 'DIALECTAL', 'HISTORICAL', 'VARIANT')
    ),
    license_identifier TEXT,
    license_url TEXT,
    copyright_status VARCHAR(32) NOT NULL CHECK (
        copyright_status IN (
            'UNKNOWN',
            'IN_COPYRIGHT',
            'PUBLIC_DOMAIN',
            'LICENSED',
            'PERMISSION_GRANTED',
            'RESTRICTED'
        )
    ),
    reviewer_name TEXT,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((reviewer_name IS NULL) = (verified_at IS NULL))
);

CREATE TABLE tildash.content_source_revisions (
    id UUID PRIMARY KEY,
    content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id),
    revision_no INTEGER NOT NULL CHECK (revision_no > 0),
    payload JSONB NOT NULL,
    provenance_id UUID NOT NULL REFERENCES tildash.content_provenance(id),
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (content_node_id, revision_no)
);

CREATE TABLE tildash.content_localization_revisions (
    id UUID PRIMARY KEY,
    content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id),
    locale VARCHAR(35) NOT NULL,
    revision_no INTEGER NOT NULL CHECK (revision_no > 0),
    variant_type VARCHAR(32) NOT NULL CHECK (
        variant_type IN ('LITERARY', 'COLLOQUIAL', 'REGIONAL', 'DIALECTAL', 'HISTORICAL', 'VARIANT')
    ),
    payload JSONB NOT NULL,
    derived_from_source_revision INTEGER NOT NULL CHECK (derived_from_source_revision > 0),
    state VARCHAR(32) NOT NULL CHECK (
        state IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')
    ),
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (content_node_id, locale, revision_no),
    FOREIGN KEY (content_node_id, derived_from_source_revision)
        REFERENCES tildash.content_source_revisions(content_node_id, revision_no)
);

CREATE TABLE tildash.content_published_versions (
    id UUID PRIMARY KEY,
    content_node_id UUID NOT NULL REFERENCES tildash.content_nodes(id),
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    source_revision_id UUID NOT NULL REFERENCES tildash.content_source_revisions(id),
    provenance_id UUID NOT NULL REFERENCES tildash.content_provenance(id),
    published_by TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE (content_node_id, version_no)
);

CREATE TABLE tildash.content_published_localizations (
    id UUID PRIMARY KEY,
    published_version_id UUID NOT NULL REFERENCES tildash.content_published_versions(id),
    locale VARCHAR(35) NOT NULL,
    variant_type VARCHAR(32) NOT NULL CHECK (
        variant_type IN ('LITERARY', 'COLLOQUIAL', 'REGIONAL', 'DIALECTAL', 'HISTORICAL', 'VARIANT')
    ),
    revision_no INTEGER NOT NULL CHECK (revision_no > 0),
    payload JSONB NOT NULL,
    localization_revision_id UUID NOT NULL REFERENCES tildash.content_localization_revisions(id),
    UNIQUE (published_version_id, locale, variant_type)
);

CREATE INDEX content_nodes_parent_idx
    ON tildash.content_nodes(parent_id);

CREATE INDEX content_nodes_state_idx
    ON tildash.content_nodes(state);

CREATE INDEX content_localization_locale_idx
    ON tildash.content_localization_revisions(locale);

CREATE INDEX content_published_localizations_locale_idx
    ON tildash.content_published_localizations(locale);

CREATE OR REPLACE FUNCTION tildash.prevent_content_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Immutable content history cannot be changed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER content_provenance_immutable
BEFORE UPDATE OR DELETE ON tildash.content_provenance
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();

CREATE TRIGGER content_source_revisions_immutable
BEFORE UPDATE OR DELETE ON tildash.content_source_revisions
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();

CREATE TRIGGER content_localization_revisions_immutable
BEFORE UPDATE OR DELETE ON tildash.content_localization_revisions
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();

CREATE TRIGGER content_published_versions_immutable
BEFORE UPDATE OR DELETE ON tildash.content_published_versions
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();

CREATE TRIGGER content_published_localizations_immutable
BEFORE UPDATE OR DELETE ON tildash.content_published_localizations
FOR EACH ROW EXECUTE FUNCTION tildash.prevent_content_history_mutation();
