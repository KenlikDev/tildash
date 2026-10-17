CREATE OR REPLACE FUNCTION tildash.prevent_published_content_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.state = 'ARCHIVED' THEN
        RAISE EXCEPTION 'Archived content cannot be mutated';
    END IF;

    IF OLD.state = 'PUBLISHED' AND (
        NEW.state IS DISTINCT FROM 'ARCHIVED'
        OR NEW.id IS DISTINCT FROM OLD.id
        OR NEW.kind IS DISTINCT FROM OLD.kind
        OR NEW.parent_id IS DISTINCT FROM OLD.parent_id
        OR NEW.source_locale IS DISTINCT FROM OLD.source_locale
        OR NEW.position IS DISTINCT FROM OLD.position
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
    ) THEN
        RAISE EXCEPTION
            'Published content may only transition to ARCHIVED without changing immutable fields';
    END IF;

    RETURN NEW;
END;
$$;
