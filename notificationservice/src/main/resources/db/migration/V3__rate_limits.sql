CREATE TABLE rate_limits (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    channel_type VARCHAR(20) NOT NULL,
    max_per_minute INT NOT NULL,
    max_per_day INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, channel_type)
);