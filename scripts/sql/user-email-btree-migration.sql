-- Run on the MySQL database containing cgv_user and uk_users_email.
ALTER TABLE cgv_user
    DROP INDEX uk_users_email,
    ADD UNIQUE INDEX uk_users_email USING BTREE (email);
