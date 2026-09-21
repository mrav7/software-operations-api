ALTER TABLE software_component
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE work_order
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE software_component
    ALTER COLUMN version DROP DEFAULT;

ALTER TABLE work_order
    ALTER COLUMN version DROP DEFAULT;
