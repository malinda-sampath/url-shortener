-- Stage 3: simulate the bulk import of the legacy SMS vendor's links.
--
-- Run with psql. Default is 5,000,000 rows; override with  -v rows=100000
-- Safe to re-run: existing seeded codes are skipped (ON CONFLICT DO NOTHING).
-- Seeded rows are easy to find and remove: their codes are exactly 8 characters
-- and start with 'L' (the app only ever generates 6-character codes).

\set ON_ERROR_STOP on
\if :{?rows}
\else
\set rows 5000000
\endif

\echo 'Rows before seeding:'
SELECT count(*) AS rows_before FROM links;

-- Make the row count visible to the DO block below (psql variables are not).
SELECT set_config('seed.rows', :'rows', false) AS rows_to_seed;

-- Seed-only speed-up for this session: don't wait for the WAL flush on each
-- COMMIT. A crash during seeding could lose the last batch, which is fine for
-- test data. Never do this for the app's own writes: it gives up the
-- durability you added in Stage 2.
SET synchronous_commit = off;

DO $$
DECLARE
total  int := current_setting('seed.rows')::int;
  batch  int := 500000;
  lo     int := 1;
  hi     int;
  hosts  text[] := ARRAY['shop.example.lk', 'offers.example.com', 'm.bank.example.lk',
                         'news.example.org', 'events.example.net', 'tickets.example.lk'];
  media  text[] := ARRAY['bulk', 'promo', 'alert', 'reminder'];
BEGIN
  WHILE lo <= total LOOP
    hi := least(lo + batch - 1, total);

INSERT INTO links (code, long_url)
SELECT
    'L' || lpad(i::text, 7, '0'),                                   -- L0000001 .. L5000000
    'https://' || hosts[1 + i % 6]
    || '/c/' || (2019 + i % 6)                                    -- campaign year
    || '/' || substr(md5(i::text), 1, 6 + i % 21)                 -- path slug, 6-26 chars
    || '?utm_source=sms&utm_medium=' || media[1 + i % 4]
    || '&utm_campaign=legacy-' || i                               -- makes every URL unique
    || CASE WHEN i % 3 = 0                                        -- a third carry an extra tracking param
    THEN '&ref=' || substr(md5((i * 7)::text), 1, 4 + i % 25)
    ELSE '' END
    FROM generate_series(lo, hi) AS i
    ON CONFLICT (code) DO NOTHING;

COMMIT;                                                           -- one transaction per batch
RAISE NOTICE 'seeded up to row % of %', hi, total;
    lo := hi + 1;
END LOOP;
END $$;

RESET synchronous_commit;

-- Clean up after the bulk load and refresh the planner's statistics,
-- as you would after any real data migration.
VACUUM (ANALYZE) links;

\echo 'Result:'
SELECT
    count(*)                                              AS rows_after,
    count(*) FILTER (WHERE length(code) = 8 AND code LIKE 'L%') AS seeded_rows,
    min(length(long_url))                                 AS min_url_len,
    round(avg(length(long_url)))                          AS avg_url_len,
    max(length(long_url))                                 AS max_url_len,
    pg_size_pretty(pg_relation_size('links'))             AS table_size,
    pg_size_pretty(pg_indexes_size('links'))              AS index_size,
    pg_size_pretty(pg_total_relation_size('links'))       AS total_size
FROM links;