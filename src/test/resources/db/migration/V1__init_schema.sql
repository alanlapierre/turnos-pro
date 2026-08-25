CREATE TABLE IF NOT EXISTS schedules (
    id UUID NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL,
    slots JSONB NOT NULL,
    CONSTRAINT pk_schedules PRIMARY KEY (tenant_id, id)
    );

CREATE INDEX IF NOT EXISTS idx_schedules_id ON schedules(id);