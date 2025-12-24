-- Role Management Schema
-- Run this after initial_schema.sql

-- Permissions table - defines all available permissions
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    resource VARCHAR(50) NOT NULL, -- e.g., 'dashboard', 'vendors', 'products', 'orders', 'users', 'roles'
    action VARCHAR(50) NOT NULL, -- e.g., 'view', 'create', 'update', 'delete', 'manage'
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Roles table - defines admin roles
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_system_role BOOLEAN DEFAULT FALSE, -- System roles cannot be deleted
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Role permissions junction table
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);

-- User roles junction table - links users to roles
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_by UUID REFERENCES users(id),
    assigned_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_permissions_resource ON permissions(resource);
CREATE INDEX IF NOT EXISTS idx_permissions_action ON permissions(action);

-- Insert default permissions for admin panel
INSERT INTO permissions (name, description, resource, action) VALUES
-- Dashboard permissions
('dashboard.view', 'View dashboard overview', 'dashboard', 'view'),
('dashboard.manage', 'Full dashboard management', 'dashboard', 'manage'),

-- Vendor management permissions
('vendors.view', 'View vendors list', 'vendors', 'view'),
('vendors.create', 'Create new vendors', 'vendors', 'create'),
('vendors.update', 'Update vendor information', 'vendors', 'update'),
('vendors.delete', 'Delete vendors', 'vendors', 'delete'),
('vendors.approve', 'Approve/reject vendors', 'vendors', 'approve'),
('vendors.manage', 'Full vendor management', 'vendors', 'manage'),

-- Product management permissions
('products.view', 'View products list', 'products', 'view'),
('products.create', 'Create new products', 'products', 'create'),
('products.update', 'Update product information', 'products', 'update'),
('products.delete', 'Delete products', 'products', 'delete'),
('products.approve', 'Approve/reject products', 'products', 'approve'),
('products.manage', 'Full product management', 'products', 'manage'),

-- Category management permissions
('categories.view', 'View categories list', 'categories', 'view'),
('categories.create', 'Create new categories', 'categories', 'create'),
('categories.update', 'Update category information', 'categories', 'update'),
('categories.delete', 'Delete categories', 'categories', 'delete'),
('categories.manage', 'Full category management', 'categories', 'manage'),

-- Order management permissions
('orders.view', 'View orders list', 'orders', 'view'),
('orders.update', 'Update order status', 'orders', 'update'),
('orders.cancel', 'Cancel orders', 'orders', 'cancel'),
('orders.manage', 'Full order management', 'orders', 'manage'),

-- User management permissions
('users.view', 'View users list', 'users', 'view'),
('users.create', 'Create new users', 'users', 'create'),
('users.update', 'Update user information', 'users', 'update'),
('users.delete', 'Delete users', 'users', 'delete'),
('users.manage', 'Full user management', 'users', 'manage'),

-- Commission management permissions
('commissions.view', 'View commission rates', 'commissions', 'view'),
('commissions.update', 'Update commission rates', 'commissions', 'update'),
('commissions.manage', 'Full commission management', 'commissions', 'manage'),

-- Voucher management permissions
('vouchers.view', 'View vouchers list', 'vouchers', 'view'),
('vouchers.create', 'Create new vouchers', 'vouchers', 'create'),
('vouchers.update', 'Update voucher information', 'vouchers', 'update'),
('vouchers.delete', 'Delete vouchers', 'vouchers', 'delete'),
('vouchers.manage', 'Full voucher management', 'vouchers', 'manage'),

-- Role management permissions
('roles.view', 'View roles list', 'roles', 'view'),
('roles.create', 'Create new roles', 'roles', 'create'),
('roles.update', 'Update role information', 'roles', 'update'),
('roles.delete', 'Delete roles', 'roles', 'delete'),
('roles.assign', 'Assign roles to users', 'roles', 'assign'),
('roles.manage', 'Full role management', 'roles', 'manage')
ON CONFLICT (name) DO NOTHING;

-- Insert default roles
INSERT INTO roles (name, description, is_system_role) VALUES
('Super Admin', 'Full access to all admin panel features', TRUE),
('Admin', 'Standard admin with most permissions', TRUE),
('Support', 'Support staff with limited permissions', FALSE),
('Moderator', 'Content moderator with product/vendor approval permissions', FALSE)
ON CONFLICT (name) DO NOTHING;

-- Assign all permissions to Super Admin role
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    (SELECT id FROM roles WHERE name = 'Super Admin'),
    id
FROM permissions
ON CONFLICT DO NOTHING;

-- Assign most permissions to Admin role (except role management)
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    (SELECT id FROM roles WHERE name = 'Admin'),
    id
FROM permissions
WHERE name NOT LIKE 'roles.%'
ON CONFLICT DO NOTHING;

-- Assign limited permissions to Support role
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    (SELECT id FROM roles WHERE name = 'Support'),
    id
FROM permissions
WHERE name IN (
    'dashboard.view',
    'orders.view',
    'orders.update',
    'users.view',
    'products.view'
)
ON CONFLICT DO NOTHING;

-- Assign moderation permissions to Moderator role
INSERT INTO role_permissions (role_id, permission_id)
SELECT 
    (SELECT id FROM roles WHERE name = 'Moderator'),
    id
FROM permissions
WHERE name IN (
    'dashboard.view',
    'vendors.view',
    'vendors.approve',
    'products.view',
    'products.approve',
    'categories.view',
    'orders.view'
)
ON CONFLICT DO NOTHING;

