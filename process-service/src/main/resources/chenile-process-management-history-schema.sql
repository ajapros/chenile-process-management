-- Additive PostgreSQL migration. Apply before deploying the updated process/trigger modules.
-- TriggerLog stays unchanged and is used only for trigger idempotency.
CREATE TABLE IF NOT EXISTS chenile_trigger_execution (
    id varchar(255) PRIMARY KEY,
    tenant varchar(255),
    trigger_id varchar(255) NOT NULL,
    event_name varchar(255) NOT NULL,
    source varchar(255),
    crontab_id varchar(255),
    trigger_time timestamptz NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    status varchar(255),
    error text,
    payload text
);
CREATE INDEX IF NOT EXISTS idx_trigger_execution_tenant_time ON chenile_trigger_execution(tenant, started_at);
CREATE INDEX IF NOT EXISTS idx_trigger_execution_trigger ON chenile_trigger_execution(tenant, trigger_id);
CREATE INDEX IF NOT EXISTS idx_trigger_execution_crontab ON chenile_trigger_execution(tenant, crontab_id);

CREATE TABLE IF NOT EXISTS chenile_process_completion_event (
    process_id varchar(255) PRIMARY KEY,
    tenant varchar(255),
    trigger_id varchar(255),
    generated_at varchar(255) NOT NULL,
    payload text NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_completion_event_tenant_trigger ON chenile_process_completion_event(tenant, trigger_id);

-- Match the host's Hibernate physical naming strategy if it differs from Spring's snake_case.
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_created ON process_table(tenant, created_time, id);
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_trigger ON process_table(tenant, trigger_id);
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_parent ON process_table(tenant, parent_id);
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_predecessor ON process_table(tenant, predecessor_id);
