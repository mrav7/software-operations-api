CREATE TABLE software_component (
    id uuid NOT NULL,
    name text NOT NULL,
    description text,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_software_component PRIMARY KEY (id),
    CONSTRAINT uk_software_component_name UNIQUE (name)
);

CREATE TABLE work_order (
    id uuid NOT NULL,
    component_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    type varchar(32) NOT NULL,
    priority varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    target_version text,
    blocking_reason text,
    blocked_at timestamptz,
    resolution_summary text,
    cancellation_reason text,
    created_at timestamptz NOT NULL,
    planned_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_work_order PRIMARY KEY (id),
    CONSTRAINT fk_work_order_component FOREIGN KEY (component_id)
        REFERENCES software_component (id) ON DELETE RESTRICT,
    CONSTRAINT chk_work_order_type CHECK (type IN (
        'DEPLOYMENT',
        'CORRECTIVE_MAINTENANCE',
        'PREVENTIVE_MAINTENANCE',
        'OPERATIONAL_SUPPORT'
    )),
    CONSTRAINT chk_work_order_priority CHECK (priority IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    CONSTRAINT chk_work_order_status CHECK (status IN (
        'CREATED', 'PLANNED', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED'
    ))
);
