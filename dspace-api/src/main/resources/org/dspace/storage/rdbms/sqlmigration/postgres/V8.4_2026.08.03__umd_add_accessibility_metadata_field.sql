--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-- Flyway Migration: Add local.accessibility.acknowledged metadata field
-- Ensures the row isn't duplicated if run multiple times

INSERT INTO metadatafieldregistry (metadata_schema_id, element, qualifier, scope_note)
SELECT
    ms.metadata_schema_id,
    'accessibility' AS element,
    'acknowledged' AS qualifier,
    'User acknowledgment that submission is subject to DRUM accessibility policies.' AS scope_note
FROM metadataschemaregistry ms
WHERE ms.short_id = 'local'
  AND NOT EXISTS (
        SELECT 1
        FROM metadatafieldregistry mfr
        WHERE mfr.metadata_schema_id = ms.metadata_schema_id
          AND mfr.element = 'accessibility'
          AND mfr.qualifier = 'acknowledged'
    );
