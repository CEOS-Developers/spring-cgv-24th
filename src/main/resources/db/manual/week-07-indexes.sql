-- MySQL 8.4 / InnoDB. Apply once to a database created by an earlier week (deploy profile uses ddl-auto update).
-- Index for ScreeningRepository.findAllByMovie_IdOrderByStartAt: where movie_id = ? order by start_at.
-- The equality column comes first and the sort column second, so one index range returns rows already sorted.
-- It also serves movie_id-only lookups (leftmost prefix), so it replaces the index MySQL created for the movie FK.
--
-- Check first:
--   SHOW CREATE TABLE screenings;
--   SHOW INDEX FROM screenings;
-- 1. Starting the application with ddl-auto update also creates idx_screening_movie_start_at from the @Index on
--    Screening. If SHOW INDEX already lists it, skip this file.
-- 2. MySQL has no ADD INDEX IF NOT EXISTS. Running the statement twice fails with error 1061 (duplicate key name)
--    and changes nothing, so a repeat is safe but noisy.
-- 3. ALGORITHM=INPLACE, LOCK=NONE makes MySQL refuse the statement instead of blocking writes if it cannot run online.
--    Take a backup first; building the index scans the whole table.
--
-- Afterwards run SHOW INDEX FROM screenings again. MySQL may silently drop the index it created automatically for the
-- movie_id foreign key once another index can enforce that constraint; the FK constraint itself stays. A database whose
-- FK index was created by Hibernate under a generated name (FK...) behaves the same way.

ALTER TABLE screenings
    ADD INDEX idx_screening_movie_start_at (movie_id, start_at),
    ALGORITHM=INPLACE, LOCK=NONE;

-- Rollback (not part of the normal run). The foreign key needs an index on movie_id, so add one in the same statement:
-- ALTER TABLE screenings
--     ADD INDEX idx_screening_movie (movie_id),
--     DROP INDEX idx_screening_movie_start_at;
