package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.model.Permission;
import com.Daad.ecommerce.model.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.UUID;

@Repository
public class RoleRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Permission RowMapper
    private final RowMapper<Permission> permissionRowMapper = new RowMapper<Permission>() {
        @Override
        public Permission mapRow(ResultSet rs, int rowNum) throws SQLException {
            Permission permission = new Permission();
            permission.setId(rs.getString("id"));
            permission.setName(rs.getString("name"));
            permission.setDescription(rs.getString("description"));
            permission.setResource(rs.getString("resource"));
            permission.setAction(rs.getString("action"));
            if (rs.getTimestamp("created_at") != null) {
                permission.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            }
            if (rs.getTimestamp("updated_at") != null) {
                permission.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            }
            return permission;
        }
    };

    // Role RowMapper
    private final RowMapper<Role> roleRowMapper = new RowMapper<Role>() {
        @Override
        public Role mapRow(ResultSet rs, int rowNum) throws SQLException {
            Role role = new Role();
            role.setId(rs.getString("id"));
            role.setName(rs.getString("name"));
            role.setDescription(rs.getString("description"));
            role.setIsSystemRole(rs.getBoolean("is_system_role"));
            if (rs.getTimestamp("created_at") != null) {
                role.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            }
            if (rs.getTimestamp("updated_at") != null) {
                role.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            }
            return role;
        }
    };

    // Get all permissions
    public List<Permission> getAllPermissions() {
        String sql = "SELECT * FROM permissions ORDER BY resource, action";
        return jdbcTemplate.query(sql, permissionRowMapper);
    }

    // Get permission by ID
    public Permission getPermissionById(String id) {
        String sql = "SELECT * FROM permissions WHERE id = ?";
        List<Permission> permissions = jdbcTemplate.query(sql, permissionRowMapper, UUID.fromString(id));
        return permissions.isEmpty() ? null : permissions.get(0);
    }

    // Get all roles
    public List<Role> getAllRoles() {
        try {
            String sql = "SELECT * FROM roles ORDER BY name";
            List<Role> roles = jdbcTemplate.query(sql, roleRowMapper);
            // Load permissions for each role
            for (Role role : roles) {
                try {
                    role.setPermissions(getPermissionsByRoleId(role.getId()));
                } catch (Exception e) {
                    // If permissions can't be loaded, set empty list and continue
                    role.setPermissions(new ArrayList<>());
                    System.err.println("Error loading permissions for role " + role.getId() + ": " + e.getMessage());
                }
            }
            return roles;
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            System.err.println("Database error in getAllRoles: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLException().getSQLState());
            System.err.println("Error Code: " + e.getSQLException().getErrorCode());
            throw new RuntimeException("Roles table does not exist. Please run the role_management_schema.sql migration script.", e);
        } catch (Exception e) {
            System.err.println("Unexpected error in getAllRoles: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // Check if role exists (without loading permissions)
    public boolean roleExists(String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return false;
            }
            UUID roleUuid = UUID.fromString(id);
            String sql = "SELECT COUNT(*) FROM roles WHERE id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, roleUuid);
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("Error checking if role exists: " + id + ", Error: " + e.getMessage());
            return false;
        }
    }

    // Get role by ID
    public Role getRoleById(String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return null;
            }
            UUID roleUuid = UUID.fromString(id);
            String sql = "SELECT * FROM roles WHERE id = ?";
            List<Role> roles = jdbcTemplate.query(sql, roleRowMapper, roleUuid);
            if (roles.isEmpty()) {
                return null;
            }
            Role role = roles.get(0);
            // Load permissions - handle errors gracefully
            try {
                role.setPermissions(getPermissionsByRoleId(role.getId()));
            } catch (Exception e) {
                System.err.println("Error loading permissions for role " + role.getId() + ": " + e.getMessage());
                role.setPermissions(new ArrayList<>());
            }
            return role;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID format for role id: " + id);
            return null;
        } catch (Exception e) {
            System.err.println("Error getting role by id: " + id + ", Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Get permissions by role ID
    public List<Permission> getPermissionsByRoleId(String roleId) {
        try {
            if (roleId == null || roleId.trim().isEmpty()) {
                return new ArrayList<>();
            }
            UUID roleUuid = UUID.fromString(roleId);
            String sql = "SELECT p.* FROM permissions p " +
                    "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
                    "WHERE rp.role_id = ? ORDER BY p.resource, p.action";
            return jdbcTemplate.query(sql, permissionRowMapper, roleUuid);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID format for roleId: " + roleId);
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error loading permissions for role " + roleId + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Create role
    public String createRole(String name, String description) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO roles (id, name, description, is_system_role, created_at, updated_at) " +
                "VALUES (?, ?, ?, FALSE, NOW(), NOW())";
        jdbcTemplate.update(sql, id, name, description);
        return id.toString();
    }

    // Update role
    public int updateRole(String id, String name, String description) {
        String sql = "UPDATE roles SET name = ?, description = ?, updated_at = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql, name, description, UUID.fromString(id));
    }

    // Delete role (only if not system role)
    public int deleteRole(String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return 0;
            }
            UUID roleUuid = UUID.fromString(id);
            String sql = "DELETE FROM roles WHERE id = ? AND is_system_role = FALSE";
            return jdbcTemplate.update(sql, roleUuid);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID format for role id: " + id);
            return 0;
        } catch (Exception e) {
            System.err.println("Error deleting role: " + id + ", Error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // Assign permissions to role
    public void assignPermissionsToRole(String roleId, List<String> permissionIds) {
        try {
            if (roleId == null || roleId.trim().isEmpty()) {
                throw new IllegalArgumentException("Role ID cannot be null or empty");
            }
            
            UUID roleUuid;
            try {
                roleUuid = UUID.fromString(roleId);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role ID format: " + roleId, e);
            }
            
            // First, remove all existing permissions
            String deleteSql = "DELETE FROM role_permissions WHERE role_id = ?";
            try {
                jdbcTemplate.update(deleteSql, roleUuid);
            } catch (Exception e) {
                System.err.println("Error deleting existing permissions for role " + roleId + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to delete existing permissions: " + e.getMessage(), e);
            }

            // Then, insert new permissions
            if (permissionIds != null && !permissionIds.isEmpty()) {
                String insertSql = "INSERT INTO role_permissions (role_id, permission_id, created_at) VALUES (?, ?, NOW())";
                for (String permissionId : permissionIds) {
                    if (permissionId == null || permissionId.trim().isEmpty()) {
                        System.err.println("Skipping null or empty permission ID");
                        continue;
                    }
                    try {
                        UUID permissionUuid = UUID.fromString(permissionId.trim());
                        int rowsAffected = jdbcTemplate.update(insertSql, roleUuid, permissionUuid);
                        if (rowsAffected == 0) {
                            System.err.println("Warning: No rows affected when inserting permission " + permissionId + " for role " + roleId);
                        }
                    } catch (IllegalArgumentException e) {
                        System.err.println("Invalid UUID format for permission ID: " + permissionId);
                        throw new RuntimeException("Invalid permission ID format: " + permissionId, e);
                    } catch (org.springframework.jdbc.BadSqlGrammarException e) {
                        System.err.println("SQL Grammar Error inserting permission " + permissionId + " for role " + roleId);
                        System.err.println("SQL: " + insertSql);
                        System.err.println("Role UUID: " + roleUuid);
                        System.err.println("Permission UUID: " + permissionId);
                        e.printStackTrace();
                        throw new RuntimeException("SQL error when assigning permission: " + e.getMessage(), e);
                    } catch (Exception e) {
                        System.err.println("Error inserting permission " + permissionId + " for role " + roleId + ": " + e.getMessage());
                        e.printStackTrace();
                        throw new RuntimeException("Failed to assign permission " + permissionId + " to role " + roleId + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (RuntimeException e) {
            // Re-throw runtime exceptions as-is
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected error assigning permissions to role " + roleId + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to assign permissions to role: " + e.getMessage(), e);
        }
    }

    // Get roles by user ID
    public List<Role> getRolesByUserId(String userId) {
        String sql = "SELECT r.* FROM roles r " +
                "INNER JOIN user_roles ur ON r.id = ur.role_id " +
                "WHERE ur.user_id = ? ORDER BY r.name";
        List<Role> roles = jdbcTemplate.query(sql, roleRowMapper, UUID.fromString(userId));
        // Load permissions for each role
        for (Role role : roles) {
            role.setPermissions(getPermissionsByRoleId(role.getId()));
        }
        return roles;
    }

    // Get all permissions by user ID (aggregated from all user's roles)
    public List<Permission> getPermissionsByUserId(String userId) {
        String sql = "SELECT DISTINCT p.* FROM permissions p " +
                "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
                "INNER JOIN user_roles ur ON rp.role_id = ur.role_id " +
                "WHERE ur.user_id = ? ORDER BY p.resource, p.action";
        return jdbcTemplate.query(sql, permissionRowMapper, UUID.fromString(userId));
    }

    // Assign roles to user
    public void assignRolesToUser(String userId, List<String> roleIds, String assignedBy) {
        // First, remove all existing roles
        String deleteSql = "DELETE FROM user_roles WHERE user_id = ?";
        jdbcTemplate.update(deleteSql, UUID.fromString(userId));

        // Then, insert new roles
        if (roleIds != null && !roleIds.isEmpty()) {
            String insertSql = "INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at) VALUES (?, ?, ?, NOW())";
            UUID userUuid = UUID.fromString(userId);
            UUID assignedByUuid = assignedBy != null ? UUID.fromString(assignedBy) : null;
            for (String roleId : roleIds) {
                jdbcTemplate.update(insertSql, userUuid, UUID.fromString(roleId), assignedByUuid);
            }
        }
    }

    // Remove role from user
    public int removeRoleFromUser(String userId, String roleId) {
        String sql = "DELETE FROM user_roles WHERE user_id = ? AND role_id = ?";
        return jdbcTemplate.update(sql, UUID.fromString(userId), UUID.fromString(roleId));
    }

    // Check if user has permission
    public boolean userHasPermission(String userId, String permissionName) {
        String sql = "SELECT COUNT(*) FROM permissions p " +
                "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
                "INNER JOIN user_roles ur ON rp.role_id = ur.role_id " +
                "WHERE ur.user_id = ? AND p.name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, UUID.fromString(userId), permissionName);
        return count != null && count > 0;
    }

    // Get users by role ID
    public List<Map<String, Object>> getUsersByRoleId(String roleId) {
        String sql = "SELECT u.id, u.username, u.email, u.role, ur.assigned_at " +
                "FROM users u " +
                "INNER JOIN user_roles ur ON u.id = ur.user_id " +
                "WHERE ur.role_id = ? ORDER BY u.username";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> user = new HashMap<>();
            user.put("id", rs.getString("id"));
            user.put("username", rs.getString("username"));
            user.put("email", rs.getString("email"));
            user.put("role", rs.getString("role"));
            if (rs.getTimestamp("assigned_at") != null) {
                user.put("assignedAt", rs.getTimestamp("assigned_at").toInstant().toString());
            }
            return user;
        }, UUID.fromString(roleId));
    }
}

