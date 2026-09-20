package com.example.notificationservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTenantId_whenPrincipalIsTenantScoped() {
        AppUserPrincipal principal = new AppUserPrincipal(1L, "admin@acme.com", "hashed", 42L, "ROLE_TENANT_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));

        assertThat(CurrentUser.tenantId()).isEqualTo(42L);
    }

    @Test
    void throws_whenPrincipalHasNoTenantId() {
        AppUserPrincipal principal = new AppUserPrincipal(1L, "admin@notify.local", "hashed", null, "ROLE_PLATFORM_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));

        assertThatThrownBy(CurrentUser::tenantId).isInstanceOf(IllegalStateException.class);
    }
}