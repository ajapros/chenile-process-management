-- PostgreSQL upgrade. Stop all trigger producers first. Run as ONE transaction:
-- psql --single-transaction -f chenile-trigger-unification.sql
-- Existing claim IDs/statuses are authoritative; missing history remains nullable.
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
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS tenant varchar(255);
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS source varchar(255);
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS crontab_id varchar(255);
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS started_at timestamp with time zone;
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS finished_at timestamp with time zone;
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS error text;
ALTER TABLE trigger_log ADD COLUMN IF NOT EXISTS payload text;

DO $merge$
BEGIN
    IF to_regclass('chenile_trigger_execution') IS NULL THEN RETURN; END IF;
    IF EXISTS (SELECT 1 FROM chenile_trigger_execution GROUP BY trigger_id, event_name HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Multiple legacy execution rows share a trigger/event key; reconcile before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM chenile_trigger_execution e JOIN trigger_log l ON l.id=e.id
               WHERE l.trigger_id<>e.trigger_id OR l.event_name<>e.event_name) THEN
        RAISE EXCEPTION 'Legacy execution ID conflicts with a different claim; reconcile before migration';
    END IF;
    UPDATE trigger_log l SET
        tenant=coalesce(l.tenant,e.tenant), source=coalesce(l.source,e.source),
        crontab_id=coalesce(l.crontab_id,e.crontab_id), payload=coalesce(l.payload,e.payload),
        started_at=coalesce(l.started_at,e.started_at), finished_at=coalesce(l.finished_at,e.finished_at),
        error=coalesce(l.error,e.error),
        status=CASE WHEN l.status='RECEIVED' AND e.status='RUNNING' THEN 'RUNNING' ELSE l.status END
    FROM chenile_trigger_execution e WHERE l.trigger_id=e.trigger_id AND l.event_name=e.event_name;
    INSERT INTO trigger_log
        (id,trigger_id,event_name,trigger_time,status,tenant,source,crontab_id,started_at,finished_at,error,payload)
    SELECT e.id,e.trigger_id,e.event_name,e.trigger_time,
        CASE WHEN e.status IN ('RUNNING','COMPLETED','FAILED') THEN e.status ELSE 'RECEIVED' END,
        e.tenant,e.source,e.crontab_id,e.started_at,e.finished_at,e.error,e.payload
    FROM chenile_trigger_execution e
    WHERE NOT EXISTS (SELECT 1 FROM trigger_log l WHERE l.trigger_id=e.trigger_id AND l.event_name=e.event_name);
    DROP TABLE chenile_trigger_execution;
END
$merge$;
CREATE INDEX IF NOT EXISTS idx_trigger_log_tenant_time ON trigger_log(tenant, started_at);
CREATE INDEX IF NOT EXISTS idx_trigger_log_tenant_trigger ON trigger_log(tenant, trigger_id);
CREATE INDEX IF NOT EXISTS idx_trigger_log_crontab ON trigger_log(tenant, crontab_id);
