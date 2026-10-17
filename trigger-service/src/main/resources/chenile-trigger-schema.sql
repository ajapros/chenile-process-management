-- One retained row per trigger/event: idempotency identity and dispatch lifecycle.
CREATE TABLE IF NOT EXISTS trigger_log (
    id varchar(255) PRIMARY KEY,
    created_time timestamp, last_modified_time timestamp,
    created_by varchar(255), last_modified_by varchar(255),
    version bigint NOT NULL DEFAULT 0,
    trigger_id varchar(255) NOT NULL,
    event_name varchar(255) NOT NULL,
    trigger_time timestamp with time zone NOT NULL,
    status varchar(255) NOT NULL,
    tenant varchar(255), source varchar(255),
    started_at timestamp with time zone, finished_at timestamp with time zone,
    error text, payload text, trigger_context text,
    CONSTRAINT uk_trigger_log_trigger_event UNIQUE (trigger_id, event_name)
);
CREATE INDEX IF NOT EXISTS idx_trigger_log_tenant_time ON trigger_log(tenant, started_at);
CREATE INDEX IF NOT EXISTS idx_trigger_log_tenant_trigger ON trigger_log(tenant, trigger_id);
