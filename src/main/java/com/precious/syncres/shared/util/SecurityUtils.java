package com.precious.syncres.shared.util;

import com.precious.syncres.entities.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class SecurityUtils {

    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        // Assuming we store the UUID as the username in UserDetails or have a custom UserDetails
        // For now, let's assume the username is the UUID string
        return UUID.fromString(authentication.getName());
    }
}
