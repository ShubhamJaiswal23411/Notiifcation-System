package com.example.notificationservice.support;

import com.example.notificationservice.security.AppUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class TestAuth {

    public static Authentication tenantAdmin(Long userId, String email, Long tenantId) {
        AppUserPrincipal principal = new AppUserPrincipal(userId, email, "hashed", tenantId, "ROLE_TENANT_ADMIN");
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }

    public static Authentication platformAdmin(Long userId, String email) {
        AppUserPrincipal principal = new AppUserPrincipal(userId, email, "hashed", null, "ROLE_PLATFORM_ADMIN");
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
    }
}