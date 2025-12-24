package com.Daad.ecommerce.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class PermissionChecker {
    
    /**
     * Check if the current user has a specific permission
     * @param permissionName The permission name (e.g., "vendors.view", "products.approve")
     * @return true if user has the permission, false otherwise
     */
    public static boolean hasPermission(String permissionName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        
        String permissionAuthority = "PERMISSION_" + permissionName;
        return auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(permissionAuthority));
    }
    
    /**
     * Check if the current user has any of the specified permissions
     * @param permissionNames Array of permission names
     * @return true if user has at least one of the permissions
     */
    public static boolean hasAnyPermission(String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (hasPermission(permissionName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if the current user has all of the specified permissions
     * @param permissionNames Array of permission names
     * @return true if user has all permissions
     */
    public static boolean hasAllPermissions(String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (!hasPermission(permissionName)) {
                return false;
            }
        }
        return true;
    }
}

