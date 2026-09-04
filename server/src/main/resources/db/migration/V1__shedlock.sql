-- ShedLock lock table for @Scheduled jobs (ingest/fetch pipeline, Phase 3+).
-- Column shapes follow net.javacrumbs.shedlock's JdbcTemplate provider contract
-- exactly (name, lock_until, locked_at, locked_by) — do not rename or reorder.
--
-- No trigger, no function, no generated column here. Content hashes and any
-- other derived value are computed in the service layer, never in the
-- database: a computed column would put the digest outside the reach of the
-- determinism tests that guarantee identical content yields an identical
-- hash across platforms.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
