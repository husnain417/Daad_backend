package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.AssignRoleRequest;
import com.Daad.ecommerce.dto.CreateRoleRequest;
import com.Daad.ecommerce.dto.UpdateRoleRequest;
import com.Daad.ecommerce.model.Permission;
import com.Daad.ecommerce.model.Role;
import com.Daad.ecommerce.repository.RoleRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/roles")
@CrossOrigin(origins = "*")
@Slf4j
public class RoleController {

    @Autowired
    private RoleRepository roleRepository;

    // Get all permissions
    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllPermissions() {
        try {
            List<Permission> permissions = roleRepository.getAllPermissions();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", permissions
            ));
        } catch (Exception e) {
            log.error("Error fetching permissions", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching permissions: " + e.getMessage()
            ));
        }
    }

    // Get all roles
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllRoles() {
        try {
            List<Role> roles = roleRepository.getAllRoles();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", roles
            ));
        } catch (Exception e) {
            log.error("Error fetching roles", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching roles: " + e.getMessage()
            ));
        }
    }

    // Get role by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRoleById(@PathVariable String id) {
        try {
            Role role = roleRepository.getRoleById(id);
            if (role == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Role not found"
                ));
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", role
            ));
        } catch (Exception e) {
            log.error("Error fetching role", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching role: " + e.getMessage()
            ));
        }
    }

    // Create role
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createRole(@RequestBody CreateRoleRequest request) {
        try {
            log.info("Creating role with request: name={}, description={}, permissionIds={}", 
                request.getName(), request.getDescription(), request.getPermissionIds());
            
            // Validate request
            if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
                log.warn("Invalid role creation request: name is null or empty");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Role name is required"
                ));
            }

            // Check if role with same name exists
            List<Role> existingRoles = roleRepository.getAllRoles();
            boolean nameExists = existingRoles.stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(request.getName().trim()));
            if (nameExists) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Role with this name already exists"
                ));
            }

            // Create role
            String roleId = roleRepository.createRole(
                request.getName().trim(),
                request.getDescription() != null ? request.getDescription().trim() : null
            );

            // Assign permissions if provided
            if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
                roleRepository.assignPermissionsToRole(roleId, request.getPermissionIds());
            }

            // Fetch created role
            Role createdRole = roleRepository.getRoleById(roleId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Role created successfully",
                "data", createdRole
            ));
        } catch (Exception e) {
            log.error("Error creating role", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error creating role: " + e.getMessage()
            ));
        }
    }

    // Update role
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateRole(
            @PathVariable String id,
            @RequestBody UpdateRoleRequest request) {
        try {
            // Check if role exists
            Role existingRole = roleRepository.getRoleById(id);
            if (existingRole == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Role not found"
                ));
            }

            // Check if it's a system role (cannot be modified)
            if (Boolean.TRUE.equals(existingRole.getIsSystemRole())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "System roles cannot be modified"
                ));
            }

            // Validate request
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Role name is required"
                ));
            }

            // Check if another role with same name exists
            List<Role> existingRoles = roleRepository.getAllRoles();
            boolean nameExists = existingRoles.stream()
                .anyMatch(r -> !r.getId().equals(id) && r.getName().equalsIgnoreCase(request.getName().trim()));
            if (nameExists) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Role with this name already exists"
                ));
            }

            // Update role
            int updated = roleRepository.updateRole(
                id,
                request.getName().trim(),
                request.getDescription() != null ? request.getDescription().trim() : null
            );

            if (updated == 0) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Role not found"
                ));
            }

            // Update permissions if provided
            if (request.getPermissionIds() != null) {
                roleRepository.assignPermissionsToRole(id, request.getPermissionIds());
            }

            // Fetch updated role
            Role updatedRole = roleRepository.getRoleById(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Role updated successfully",
                "data", updatedRole
            ));
        } catch (Exception e) {
            log.error("Error updating role", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error updating role: " + e.getMessage()
            ));
        }
    }

    // Delete role
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteRole(@PathVariable String id) {
        try {
            // Check if role exists (without loading permissions to avoid errors)
            if (!roleRepository.roleExists(id)) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Role not found"
                ));
            }

            // Get role to check if it's a system role (load permissions only if needed)
            Role existingRole = roleRepository.getRoleById(id);
            if (existingRole == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Role not found"
                ));
            }

            // Check if it's a system role (cannot be deleted)
            if (Boolean.TRUE.equals(existingRole.getIsSystemRole())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "System roles cannot be deleted"
                ));
            }

            // Delete role
            int deleted = roleRepository.deleteRole(id);
            if (deleted == 0) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Role not found or cannot be deleted"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Role deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting role", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error deleting role: " + e.getMessage()
            ));
        }
    }

    // Get roles by user ID
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRolesByUserId(@PathVariable String userId) {
        try {
            List<Role> roles = roleRepository.getRolesByUserId(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", roles
            ));
        } catch (Exception e) {
            log.error("Error fetching user roles", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching user roles: " + e.getMessage()
            ));
        }
    }

    // Get permissions by user ID
    @GetMapping("/user/{userId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPermissionsByUserId(@PathVariable String userId) {
        try {
            List<Permission> permissions = roleRepository.getPermissionsByUserId(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", permissions
            ));
        } catch (Exception e) {
            log.error("Error fetching user permissions", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching user permissions: " + e.getMessage()
            ));
        }
    }

    // Assign roles to user
    @PostMapping("/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> assignRolesToUser(@RequestBody AssignRoleRequest request) {
        try {
            log.info("Assigning roles to user: userId={}, roleIds={}", 
                request.getUserId(), request.getRoleIds());
            
            // Validate request
            if (request == null || request.getUserId() == null || request.getUserId().trim().isEmpty()) {
                log.warn("Invalid role assignment request: userId is null or empty");
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "User ID is required"
                ));
            }

            String currentUserId = SecurityUtils.currentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            // Assign roles
            roleRepository.assignRolesToUser(
                request.getUserId().trim(),
                request.getRoleIds() != null ? request.getRoleIds() : List.of(),
                currentUserId
            );

            // Fetch updated roles
            List<Role> roles = roleRepository.getRolesByUserId(request.getUserId().trim());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Roles assigned successfully",
                "data", roles
            ));
        } catch (Exception e) {
            log.error("Error assigning roles", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error assigning roles: " + e.getMessage()
            ));
        }
    }

    // Get users by role ID
    @GetMapping("/{id}/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUsersByRoleId(@PathVariable String id) {
        try {
            List<Map<String, Object>> users = roleRepository.getUsersByRoleId(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", users
            ));
        } catch (Exception e) {
            log.error("Error fetching users by role", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching users by role: " + e.getMessage()
            ));
        }
    }

    // Check if current user has permission
    @GetMapping("/check-permission/{permissionName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> checkPermission(@PathVariable String permissionName) {
        try {
            String currentUserId = SecurityUtils.currentUserId();
            if (currentUserId == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "hasPermission", false,
                    "message", "Unauthorized"
                ));
            }

            boolean hasPermission = roleRepository.userHasPermission(currentUserId, permissionName);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "hasPermission", hasPermission,
                "permission", permissionName
            ));
        } catch (Exception e) {
            log.error("Error checking permission", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "hasPermission", false,
                "message", "Error checking permission: " + e.getMessage()
            ));
        }
    }
}

