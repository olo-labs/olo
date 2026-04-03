-- Copyright (c) 2026 Olo Labs. All rights reserved.

-- Mapping of tenant IDs to region. Used with config key "olo.region" (comma-separated list of regions this instance serves).
-- Cached in Redis hash olo:worker:tenant:region (HSET/HGET) for fast lookup.
--
-- For existing DBs with the old schema, run a migration to alter column sizes and add the index; or recreate with DROP + this DDL.

CREATE TABLE IF NOT EXISTS olo_configuration_region (
    tenant_id  VARCHAR(64) PRIMARY KEY,
    region     VARCHAR(64) NOT NULL DEFAULT 'default',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_olo_configuration_region_region
ON olo_configuration_region(region);

-- Example: assign tenants to regions
-- INSERT INTO olo_configuration_region (tenant_id, region) VALUES
-- ('tenant-a', 'default'),
-- ('tenant-b', 'us-east'),
-- ('tenant-c', 'eu-west');
--
-- Update (set updated_at explicitly):
-- UPDATE olo_configuration_region SET region = 'us-east', updated_at = CURRENT_TIMESTAMP WHERE tenant_id = 'tenant-a';
