package com.example.notificationservice.security;

import com.example.notificationservice.domain.PlatformAdmin;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.TenantAdmin;
import com.example.notificationservice.repository.PlatformAdminRepository;
import com.example.notificationservice.repository.TenantAdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AppUserDetailsServiceTest {

    @Mock private PlatformAdminRepository platformAdminRepository;
    @Mock private TenantAdminRepository tenantAdminRepository;

    @InjectMocks private AppUserDetailsService service;

    @Test
    void loadsplatformAdmin_whenEmailMatchesPlatformAdmin() {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(1L);
        admin.setEmail("admin@notify.local");
        admin.setPasswordHash("hashed");
        when(platformAdminRepository.findByEmail("admin@notify.local")).thenReturn(Optional.of(admin));

        UserDetails result = service.loadUserByUsername("admin@notify.local");

        assertThat(result.getUsername()).isEqualTo("admin@notify.local");
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_PLATFORM_ADMIN");
        assertThat(((AppUserPrincipal) result).getTenantId()).isNull();
    }

    @Test
    void loadsTenantAdmin_whenNoPlatformAdminMatches() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        TenantAdmin admin = new TenantAdmin();
        admin.setId(2L);
        admin.setTenant(tenant);
        admin.setEmail("admin@acme.com");
        admin.setPasswordHash("hashed");

        when(platformAdminRepository.findByEmail("admin@acme.com")).thenReturn(Optional.empty());
        when(tenantAdminRepository.findByEmail("admin@acme.com")).thenReturn(Optional.of(admin));

        UserDetails result = service.loadUserByUsername("admin@acme.com");

        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_TENANT_ADMIN");
        assertThat(((AppUserPrincipal) result).getTenantId()).isEqualTo(7L);
    }

    @Test
    void throwsWhenNoUserMatchesEither() {
        when(platformAdminRepository.findByEmail("ghost@nowhere.com")).thenReturn(Optional.empty());
        when(tenantAdminRepository.findByEmail("ghost@nowhere.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@nowhere.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}