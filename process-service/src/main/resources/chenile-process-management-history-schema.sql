-- Additive PostgreSQL migration. Apply before deploying the updated process/trigger modules.
-- Apply trigger-service/chenile-trigger-schema.sql for a fresh unified trigger table.
-- Match the host's Hibernate physical naming strategy if it differs from Spring's snake_case.
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_created ON process_table(tenant, created_time, id);
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_trigger ON process_table(tenant, trigger_id);
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_parent ON process_table(tenant, parent_id);
CREATE INDEX IF NOT EXISTS idx_process_management_tenant_predecessor ON process_table(tenant, predecessor_id);
