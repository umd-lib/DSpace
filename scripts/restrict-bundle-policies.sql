-- restrict-bundle-policies.sql
--
-- One-off backfill that makes the restricted bundles of EXISTING items
-- admin-only, matching what DSpace 9.4 does for new content when
-- "core.authorization.restricted-bundle" is configured: every resource policy
-- on the bundle and on each of its bitstreams is removed (see
-- ItemServiceImpl.adjustBundleBitstreamPolicies/addBundle,
-- BundleServiceImpl.addBitstream and
-- MediaFilterServiceImpl.updatePoliciesOfDerivativeBitstream).
--
-- Scope:
--   * Bundles named in "restricted_name" below (keep in sync with the
--     "core.authorization.restricted-bundle" values in local.cfg).
--   * Belonging to archived OR withdrawn items. Withdrawn items are included
--     because ItemServiceImpl.reinstate() switches WITHDRAWN_READ policies
--     back to READ, which would re-open these bundles on reinstatement.
--   * Workspace/workflow items are skipped. InstallItem clears restricted
--     bundles when they are archived.
--   * A bitstream that is ALSO in a bundle outside the target set (e.g. both
--     ORIGINAL and TEXT) is left alone and listed for manual review.
--
-- Removed policies are copied to "drum_restricted_bundle_rp_backup" first.
-- The script is idempotent. A second run finds nothing to remove.
--
-- Usage (Postgres, psql 10+):
--   Dry run (reports, then ROLLBACK; nothing is changed):
--     psql -v ON_ERROR_STOP=1 -f restrict-bundle-policies.sql
--   Apply (reports, then COMMIT):
--     psql -v ON_ERROR_STOP=1 -v apply=1 -f restrict-bundle-policies.sql
--
-- Rollback after an applied run:
--   INSERT INTO resourcepolicy SELECT * FROM drum_restricted_bundle_rp_backup
--     ON CONFLICT (policy_id) DO NOTHING;

\set QUIET on
\pset footer off

BEGIN;

CREATE TEMP TABLE restricted_name (name varchar PRIMARY KEY) ON COMMIT DROP;
INSERT INTO restricted_name VALUES
    ('TEXT'), ('LICENSE'), ('SWORD'), ('METADATA'), ('PRESERVATION'), ('CC-LICENSE');

-- Bundle name is the first dc.title value (Bundle.getName())
CREATE TEMP TABLE bundle_name ON COMMIT DROP AS
SELECT DISTINCT ON (mv.dspace_object_id)
       mv.dspace_object_id AS bundle_id, mv.text_value AS name
FROM metadatavalue mv
JOIN bundle b ON b.uuid = mv.dspace_object_id
JOIN metadatafieldregistry mf ON mf.metadata_field_id = mv.metadata_field_id
JOIN metadataschemaregistry ms ON ms.metadata_schema_id = mf.metadata_schema_id
WHERE ms.short_id = 'dc' AND mf.element = 'title' AND mf.qualifier IS NULL
ORDER BY mv.dspace_object_id, mv.place;

-- Bundles of archived or withdrawn items
CREATE TEMP TABLE item_bundle ON COMMIT DROP AS
SELECT i.uuid AS item_id, bn.bundle_id, bn.name
FROM item i
JOIN item2bundle i2b ON i2b.item_id = i.uuid
JOIN bundle_name bn ON bn.bundle_id = i2b.bundle_id
WHERE i.in_archive OR i.withdrawn;

CREATE TEMP TABLE target_bundle ON COMMIT DROP AS
SELECT DISTINCT ib.bundle_id, ib.name
FROM item_bundle ib
JOIN restricted_name rn ON rn.name = ib.name;

CREATE TEMP TABLE shared_bitstream ON COMMIT DROP AS
SELECT DISTINCT b2b.bitstream_id
FROM bundle2bitstream b2b
JOIN target_bundle tb ON tb.bundle_id = b2b.bundle_id
WHERE EXISTS (
    SELECT 1 FROM bundle2bitstream o
    WHERE o.bitstream_id = b2b.bitstream_id
      AND NOT EXISTS (SELECT 1 FROM target_bundle t WHERE t.bundle_id = o.bundle_id));

CREATE TEMP TABLE target_dso ON COMMIT DROP AS
SELECT tb.bundle_id AS dso_id, tb.name AS bundle_name, 'bundle' AS kind
FROM target_bundle tb
UNION
SELECT b2b.bitstream_id, tb.name, 'bitstream'
FROM bundle2bitstream b2b
JOIN target_bundle tb ON tb.bundle_id = b2b.bundle_id
WHERE NOT EXISTS (SELECT 1 FROM shared_bitstream s WHERE s.bitstream_id = b2b.bitstream_id);

CREATE INDEX ON target_dso (dso_id);
ANALYZE target_dso;

-------------------------------------------------------------------------------
-- Report
-------------------------------------------------------------------------------

\echo
\echo '== All bundle names on archived/withdrawn items (check for unlisted names, e.g. THUMBNAIL) =='
SELECT ib.name,
       EXISTS (SELECT 1 FROM restricted_name rn WHERE rn.name = ib.name) AS restricted,
       count(DISTINCT ib.bundle_id) AS bundles,
       count(DISTINCT b2b.bitstream_id) AS bitstreams
FROM item_bundle ib
LEFT JOIN bundle2bitstream b2b ON b2b.bundle_id = ib.bundle_id
GROUP BY ib.name
ORDER BY ib.name;

\echo '== Bitstreams also in a non-target bundle (NOT changed; review manually) =='
SELECT s.bitstream_id, string_agg(DISTINCT bn.name, ', ') AS bundles
FROM shared_bitstream s
JOIN bundle2bitstream b2b ON b2b.bitstream_id = s.bitstream_id
JOIN bundle_name bn ON bn.bundle_id = b2b.bundle_id
GROUP BY s.bitstream_id;

\echo '== Policies to remove, by bundle / object / action / grantee =='
\echo '   (Anonymous READ / WITHDRAWN_READ rows are the publicly readable objects)'
SELECT t.bundle_name, t.kind,
       CASE rp.action_id
           WHEN 0 THEN 'READ' WHEN 1 THEN 'WRITE' WHEN 2 THEN 'DELETE'
           WHEN 3 THEN 'ADD' WHEN 4 THEN 'REMOVE' WHEN 11 THEN 'ADMIN'
           WHEN 12 THEN 'WITHDRAWN_READ' ELSE rp.action_id::text
       END AS action,
       CASE
           WHEN g.name IN ('Anonymous', 'Administrator') THEN g.name
           WHEN g.uuid IS NOT NULL THEN '(other group)'
           ELSE '(eperson)'
       END AS grantee,
       coalesce(rp.rptype, '') AS rptype,
       count(*) AS policies
FROM resourcepolicy rp
JOIN (SELECT DISTINCT dso_id, bundle_name, kind FROM target_dso) t ON t.dso_id = rp.dspace_object
LEFT JOIN epersongroup g ON g.uuid = rp.epersongroup_id
GROUP BY 1, 2, 3, 4, 5
ORDER BY 1, 2, 3, 4, 5;

-------------------------------------------------------------------------------
-- Apply
-------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS drum_restricted_bundle_rp_backup (LIKE resourcepolicy);

INSERT INTO drum_restricted_bundle_rp_backup
SELECT rp.* FROM resourcepolicy rp
WHERE rp.dspace_object IN (SELECT dso_id FROM target_dso);

\set QUIET off
\echo '== Removing policies =='
DELETE FROM resourcepolicy rp
WHERE rp.dspace_object IN (SELECT dso_id FROM target_dso);
\set QUIET on

\echo '== Remaining policies on target objects (expect 0) =='
SELECT count(*) AS remaining
FROM resourcepolicy rp
WHERE rp.dspace_object IN (SELECT dso_id FROM target_dso);

\if :{?apply}
    \echo '== COMMIT =='
    COMMIT;
\else
    \echo '== Dry run: ROLLBACK (re-run with -v apply=1 to commit) =='
    ROLLBACK;
\endif
