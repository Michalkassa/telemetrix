-- TEMPLATE 03 — A Flyway migration (the database schema)
-- Where it goes: src/main/resources/db/migration/V1__<what_it_does>.sql
--
-- RULES
--   • Name: V<version>__<description>.sql  — capital V, TWO underscores. V1, V2, V3 …
--   • On startup Flyway runs every file it has not run before, in version order, and records it in
--     the table flyway_schema_history.
--   • NEVER edit a file that has already run anywhere. Flyway stores a checksum and refuses to
--     start if it changed. To change the schema, add a new file: V2__add_isbn_to_books.sql.
--   • Write plain PostgreSQL SQL. This file IS the truth about your tables; the Java entities
--     (template 04) must match it (ddl-auto=validate checks that).

CREATE TABLE authors (
    id         BIGSERIAL    PRIMARY KEY,          -- BIGSERIAL = auto-increment 64-bit id
    name       VARCHAR(100) NOT NULL,
    country    VARCHAR(100),                      -- nullable: no NOT NULL
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now() -- TIMESTAMPTZ ↔ Java OffsetDateTime / Instant
);

CREATE TABLE books (
    id           BIGSERIAL    PRIMARY KEY,
    author_id    BIGINT       NOT NULL REFERENCES authors (id),  -- foreign key: many books → one author
    title        VARCHAR(255) NOT NULL,
    isbn         VARCHAR(20)  NOT NULL UNIQUE,                   -- UNIQUE = natural key, no duplicates
    published_on DATE,                                           -- DATE ↔ Java LocalDate
    pages        INTEGER      CHECK (pages > 0)
);

-- Index every column you filter or join on often. (Primary keys and UNIQUE get one automatically.)
CREATE INDEX idx_books_author_id ON books (author_id);

-- ─── Example of a LATER migration, in its own file V2__add_genre_to_books.sql ───────────────────
-- ALTER TABLE books ADD COLUMN genre VARCHAR(50);
