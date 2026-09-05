-- Fixed-window request counters for the anonymous read endpoints.
--
-- Rate limiting lives in PostgreSQL because PostgreSQL is the only shared store
-- this platform has: no Redis, no external cache, no SaaS counter service. The
-- limits it enforces are the ones the API contract states -- 60 requests per
-- minute on /api/v1/manifest/** and 600 on /api/v1/content/** -- keyed by client
-- IP, because those endpoints take no credentials and there is no principal to
-- key by.
--
-- One row per bucket, not one row per bucket per window. The window start is
-- stored alongside the count and the counter resets in place when a request
-- arrives in a later window, so the table's size is bounded by the number of
-- distinct callers rather than growing by a row a minute forever. A schema that
-- keyed on (bucket_key, window_start) would need a sweeper job to stay finite,
-- and a sweeper that stops running is a table that stops fitting in cache long
-- before anyone notices.

CREATE TABLE rate_limit_counters (
    -- "<scope>:<client ip>", e.g. "manifest:203.0.113.7".
    bucket_key   varchar(200) NOT NULL,
    -- Start of the window the count belongs to, truncated to the window size.
    window_start timestamptz  NOT NULL,
    hits         integer      NOT NULL,
    CONSTRAINT pk_rate_limit_counters PRIMARY KEY (bucket_key),
    CONSTRAINT ck_rate_limit_counters_hits CHECK (hits >= 0)
);
