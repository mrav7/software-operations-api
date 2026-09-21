CREATE TABLE work_log (
    id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    type varchar(32) NOT NULL,
    message text NOT NULL,
    created_at timestamptz NOT NULL,

    CONSTRAINT pk_work_log PRIMARY KEY (id),

    CONSTRAINT fk_work_log_work_order
        FOREIGN KEY (work_order_id)
        REFERENCES work_order (id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_work_log_type
        CHECK (type IN ('NOTE', 'STATUS_CHANGE'))
);
