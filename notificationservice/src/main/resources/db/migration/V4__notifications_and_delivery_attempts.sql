CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    channel_type VARCHAR(20) NOT NULL,
    template_id BIGINT REFERENCES templates(id),
    recipient VARCHAR(255) NOT NULL,
    variables_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    scheduled_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_status_scheduled ON notifications(status, scheduled_at);
CREATE INDEX idx_notifications_status_retry ON notifications(status, next_retry_at);

CREATE TABLE delivery_attempts (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notifications(id),
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_attempts_notification ON delivery_attempts(notification_id);