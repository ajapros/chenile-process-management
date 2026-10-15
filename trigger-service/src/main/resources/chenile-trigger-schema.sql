-- One retained row per trigger/event: idempotency identity and dispatch lifecycle.
CREATE TABLE IF NOT EXISTS trigger_log (
    id varchar(255) PRIMARY KEY,
    trigger_id varchar(255) NOT NULL,
    event_name varchar(255) NOT NULL,
    trigger_time timestamp with time zone NOT NULL,
    status varchar(255) NOT NULL,
    tenant varchar(255), source varchar(255), crontab_id varchar(255),
    started_at timestamp with time zone, finished_at timestamp with time zone,
    error text, payload text,
    CONSTRAINT uk_trigger_log_trigger_event UNIQUE (trigger_id, event_name)
);
CREATE INDEX IF NOT EXISTS idx_trigger_log_tenant_time ON trigger_log(tenant, started_at);
CREATE INDEX IF NOT EXISTS idx_trigger_log_tenant_trigger ON trigger_log(tenant, trigger_id);
CREATE INDEX IF NOT EXISTS idx_trigger_log_crontab ON trigger_log(tenant, crontab_id);
