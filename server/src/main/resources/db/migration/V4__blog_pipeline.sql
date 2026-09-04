-- Blog posts and the ingest pipeline behind the automatically sourced ones:
-- the whitelist of official sources, the raw updates fetched from them, and the
-- append-only audit trail that records every step and every human decision.

CREATE TABLE whitelist_sources (
    id                 uuid          NOT NULL,
    name               varchar(120)  NOT NULL,
    feed_url           varchar(2000) NOT NULL,
    -- The URL used for the independent second request that confirms a version
    -- string. Exactly one {version} placeholder; the substituted value is
    -- validated against a strict pattern and percent-encoded before it is ever
    -- placed in a URL.
    verify_url_pattern varchar(2000) NOT NULL,
    enabled            boolean       NOT NULL DEFAULT true,
    last_fetched_at    timestamptz,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_whitelist_sources PRIMARY KEY (id),
    CONSTRAINT uq_whitelist_sources_name UNIQUE (name),
    -- The trust chain cannot start on a channel anyone on the path can rewrite.
    CONSTRAINT ck_whitelist_sources_feed_url CHECK (feed_url LIKE 'https://%'),
    CONSTRAINT ck_whitelist_sources_verify_url CHECK (
        verify_url_pattern LIKE 'https://%'
        AND verify_url_pattern LIKE '%{version}%'
    )
);

CREATE TABLE source_updates (
    id                  uuid        NOT NULL,
    whitelist_source_id uuid        NOT NULL,
    raw_content         text        NOT NULL,
    version_string      varchar(64) NOT NULL,
    -- Deduplication key: an item whose hash has already been processed never
    -- becomes a second draft.
    content_hash        varchar(64)    NOT NULL,
    fetched_at          timestamptz NOT NULL,
    verify_status       varchar(16) NOT NULL DEFAULT 'PENDING',
    -- The verification chain as executed, in order, with the outcome of each
    -- check. The first failing entry is the reason a rejected update never
    -- became a draft.
    verify_checks       jsonb,
    created_at          timestamptz NOT NULL,
    CONSTRAINT pk_source_updates PRIMARY KEY (id),
    CONSTRAINT uq_source_updates_hash UNIQUE (whitelist_source_id, content_hash),
    CONSTRAINT fk_source_updates_whitelist_source
        FOREIGN KEY (whitelist_source_id) REFERENCES whitelist_sources (id),
    CONSTRAINT ck_source_updates_verify_status
        CHECK (verify_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_source_updates_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    -- Validated before the value is ever interpolated into a URL: this pattern
    -- is what keeps a path traversal, a scheme, an authority or whitespace out
    -- of a URL the server then fetches.
    CONSTRAINT ck_source_updates_version_string CHECK (version_string ~ '^[A-Za-z0-9._+-]{1,64}$')
);

CREATE INDEX ix_source_updates_source_fetched
    ON source_updates (whitelist_source_id, fetched_at DESC);

CREATE TABLE blog_posts (
    id               uuid          NOT NULL,
    slug             varchar(80)   NOT NULL,
    title            varchar(200)  NOT NULL,
    body_markdown    text          NOT NULL,
    status           varchar(20)   NOT NULL DEFAULT 'DRAFT',
    source           varchar(8)    NOT NULL,
    source_url       varchar(2000),
    source_update_id uuid,
    published_at     timestamptz,
    created_by       uuid,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_blog_posts PRIMARY KEY (id),
    CONSTRAINT uq_blog_posts_slug UNIQUE (slug),
    CONSTRAINT fk_blog_posts_source_update
        FOREIGN KEY (source_update_id) REFERENCES source_updates (id),
    CONSTRAINT fk_blog_posts_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_blog_posts_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_blog_posts_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED')),
    CONSTRAINT ck_blog_posts_source CHECK (source IN ('MANUAL', 'AUTO')),
    -- The source link is the provenance of automatically generated text: what a
    -- reader clicks to check a claim. An automatic post without one is not a
    -- post that may exist.
    CONSTRAINT ck_blog_posts_auto_source_url CHECK (
        source <> 'AUTO'
        OR (source_url IS NOT NULL AND btrim(source_url) <> '')
    ),
    CONSTRAINT ck_blog_posts_source_url_https CHECK (
        source_url IS NULL OR source_url LIKE 'https://%'
    ),
    -- source_update_id is non-null exactly when the post came from the pipeline.
    CONSTRAINT ck_blog_posts_source_update_pairing CHECK (
        (source = 'AUTO') = (source_update_id IS NOT NULL)
    ),
    CONSTRAINT ck_blog_posts_published_at CHECK (
        (status = 'PUBLISHED') = (published_at IS NOT NULL)
    )
);

-- The public blog list reads published posts, newest first.
CREATE INDEX ix_blog_posts_published ON blog_posts (published_at DESC)
    WHERE status = 'PUBLISHED';
-- The review queue reads PENDING_REVIEW posts, oldest first; the admin list
-- filters by status.
CREATE INDEX ix_blog_posts_status_created ON blog_posts (status, created_at);

-- Append-only. Every fetch, verification, draft and human decision lands here,
-- in the same transaction as the change it describes: a transition that cannot
-- be audited does not happen. There is no write endpoint for this table.
CREATE TABLE pipeline_audit_log (
    id                  uuid         NOT NULL,
    step                varchar(16)  NOT NULL,
    blog_post_id        uuid,
    source_update_id    uuid,
    whitelist_source_id uuid,
    -- Null for machine steps, non-null for every human decision. That
    -- distinction is the audit trail's whole purpose.
    actor_user_id       uuid,
    from_status         varchar(20),
    to_status           varchar(20),
    reason              varchar(500),
    occurred_at         timestamptz  NOT NULL,
    CONSTRAINT pk_pipeline_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_audit_log_post FOREIGN KEY (blog_post_id) REFERENCES blog_posts (id),
    CONSTRAINT fk_pipeline_audit_log_source_update
        FOREIGN KEY (source_update_id) REFERENCES source_updates (id),
    CONSTRAINT fk_pipeline_audit_log_whitelist_source
        FOREIGN KEY (whitelist_source_id) REFERENCES whitelist_sources (id),
    CONSTRAINT fk_pipeline_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT ck_pipeline_audit_log_step CHECK (
        step IN ('FETCH', 'NORMALIZE', 'VERIFY', 'DRAFT', 'SUBMIT',
                 'APPROVE', 'REJECT', 'PUBLISH', 'UNPUBLISH')
    ),
    CONSTRAINT ck_pipeline_audit_log_from_status CHECK (
        from_status IS NULL
        OR from_status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED')
    ),
    CONSTRAINT ck_pipeline_audit_log_to_status CHECK (
        to_status IS NULL
        OR to_status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED')
    )
);

-- The audit log of one post, in the order it happened.
CREATE INDEX ix_pipeline_audit_log_post ON pipeline_audit_log (blog_post_id, occurred_at);
