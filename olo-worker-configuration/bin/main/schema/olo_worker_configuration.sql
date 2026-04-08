-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- Table for storing worker configuration (e.g. Temporal settings).
-- Values are cached in Redis for fast access; this table is the source of truth.

CREATE TABLE IF NOT EXISTS olo_worker_configuration (
    config_key   VARCHAR(255) PRIMARY KEY,
    config_value TEXT,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Example: Temporal configuration
-- INSERT INTO olo_worker_configuration (config_key, config_value) VALUES
-- ('temporal.target', 'localhost:47233'),
-- ('temporal.namespace', 'default'),
-- ('temporal.task_queue', 'olo-chat-queue-ollama');
