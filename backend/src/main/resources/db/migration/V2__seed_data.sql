-- =============================================================================
-- V2 — Seed Data: one demo user for CRUD demos (no auth yet)
-- =============================================================================

INSERT INTO users (id, username, email, full_name, account_status, privacy_level)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'demo_user',
    'demo@example.com',
    'Demo User',
    'ACTIVE',
    'PUBLIC'
) ON CONFLICT (id) DO NOTHING;



