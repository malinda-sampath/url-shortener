-- Removes only the seeded legacy rows; links created through the app are kept.
\set ON_ERROR_STOP on
DELETE FROM links WHERE length(code) = 8 AND code LIKE 'L%';
VACUUM (ANALYZE) links;
SELECT count(*) AS rows_left FROM links;