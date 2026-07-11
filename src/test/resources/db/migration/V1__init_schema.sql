CREATE TABLE IF NOT EXISTS schedules (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL,
    slots JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_schedules_tenant ON schedules(tenant_id);