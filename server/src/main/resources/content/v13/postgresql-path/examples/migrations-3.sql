-- Renaming is instant for the database and fatal for whatever is still running
-- against the old name.
CREATE TABLE person (id integer PRIMARY KEY, name text NOT NULL);
INSERT INTO person VALUES (1, 'Ada Lovelace'), (2, 'Bo Nilsson');

ALTER TABLE person RENAME COLUMN name TO full_name;

-- The query the previous release is still sending.
SELECT id, name FROM person ORDER BY id;

DROP TABLE person;

-- Expand and contract does the same job in steps, each of which is safe to
-- deploy on its own while both releases are running.
CREATE TABLE person (id integer PRIMARY KEY, name text NOT NULL);
INSERT INTO person VALUES (1, 'Ada Lovelace'), (2, 'Bo Nilsson');

-- Step 1, expand: the new column appears, nullable, and is filled in.
ALTER TABLE person ADD COLUMN full_name text;
UPDATE person SET full_name = name WHERE full_name IS NULL;

-- Step 2: both names stay in step while both releases are writing. The trigger
-- exists only for the length of the migration and is dropped in step 4.
CREATE FUNCTION sync_person_name() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.full_name IS DISTINCT FROM OLD.full_name THEN
        NEW.name := NEW.full_name;
    ELSE
        NEW.full_name := NEW.name;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_person_name BEFORE UPDATE ON person
    FOR EACH ROW EXECUTE FUNCTION sync_person_name();

-- The old release writes name; the new release writes full_name. Both work.
UPDATE person SET name = 'Ada Byron' WHERE id = 1;
UPDATE person SET full_name = 'Bo Nystrom' WHERE id = 2;

SELECT id, name, full_name FROM person ORDER BY id;

-- Steps 3 and 4, contract: once no release reads the old name, drop the scaffolding.
DROP TRIGGER tr_person_name ON person;
DROP FUNCTION sync_person_name();
ALTER TABLE person DROP COLUMN name;
ALTER TABLE person ALTER COLUMN full_name SET NOT NULL;

SELECT id, full_name FROM person ORDER BY id;

-- Building an index the ordinary way locks out writers for the duration.
-- CONCURRENTLY does not, at the price of not being transactional -- which is
-- exactly why a migration tool that wraps each step in a transaction cannot
-- run it.
BEGIN;
CREATE INDEX CONCURRENTLY ix_person_full_name ON person (full_name);
COMMIT;

-- Outside a transaction block it is allowed.
CREATE INDEX CONCURRENTLY ix_person_full_name ON person (full_name);

SELECT indexname FROM pg_indexes WHERE tablename = 'person' ORDER BY indexname;

DROP TABLE person;
