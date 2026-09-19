package com.example.notificationservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUser {

    public static Long tenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        if (principal.getTenantId() == null) {
            throw new IllegalStateException("Caller is not tenant-scoped");
        }
        return principal.getTenantId();
    }
}