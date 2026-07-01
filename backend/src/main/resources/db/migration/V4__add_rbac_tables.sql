-- Create roles table
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create permissions table
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create role_permissions join table
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Create user_roles join table
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- Add index for efficient user role lookup
CREATE INDEX idx_user_roles_user ON user_roles(user_id);

--SEED DATA
INSERT INTO roles(name, is_system)
VALUES ('USER', true),
       ('MODERATOR', true),
       ('ADMIN', true),
       ('SUPER_ADMIN', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions(name, description) VALUES
('REPORT_VIEW', 'Can view reports'),
('REPORT_REVIEW', 'Can review and process reports'),
('CONTENT_MODERATE', 'Can delete/hide user content'),
('USER_VIEW', 'Can view user profiles'),
('USER_SUSPEND', 'Can suspend users'),
('USER_UNSUSPEND', 'Can unsuspend users'),
('AUDIT_LOG_VIEW', 'Can view audit logs'),
('ROLE_VIEW', 'Can view roles'),
('ROLE_ASSIGN', 'Can assign roles to users'),
('ROLE_PERMISSION_MANAGE', 'Can manage role-permission mappings')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (r.name = 'MODERATOR'   AND p.name IN ('REPORT_VIEW', 'REPORT_REVIEW', 'CONTENT_MODERATE', 'USER_VIEW'))
   OR (r.name = 'ADMIN'       AND p.name IN ('REPORT_VIEW', 'REPORT_REVIEW', 'CONTENT_MODERATE', 'USER_VIEW', 'USER_SUSPEND', 'USER_UNSUSPEND', 'AUDIT_LOG_VIEW', 'ROLE_VIEW', 'ROLE_ASSIGN'))
   OR (r.name = 'SUPER_ADMIN' AND p.name IN ('REPORT_VIEW', 'REPORT_REVIEW', 'CONTENT_MODERATE', 'USER_VIEW', 'USER_SUSPEND', 'USER_UNSUSPEND', 'AUDIT_LOG_VIEW', 'ROLE_VIEW', 'ROLE_ASSIGN', 'ROLE_PERMISSION_MANAGE'));

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE r.name = 'USER';

-- Bootstrap dev super-admin from V2 seed user
INSERT INTO user_roles (user_id, role_id)
SELECT '00000000-0000-0000-0000-000000000001', r.id
FROM roles r
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
