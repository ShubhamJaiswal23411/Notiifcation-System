package com.example.notificationservice.security;

import com.example.notificationservice.domain.PlatformAdmin;
import com.example.notificationservice.domain.TenantAdmin;
import com.example.notificationservice.repository.PlatformAdminRepository;
import com.example.notificationservice.repository.TenantAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final PlatformAdminRepository platformAdminRepository;
    private final TenantAdminRepository tenantAdminRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return platformAdminRepository.findByEmail(email)
                .<UserDetails>map(this::toPrincipal)
                .or(() -> tenantAdminRepository.findByEmail(email).map(this::toPrincipal))
                .orElseThrow(() -> new UsernameNotFoundException("No user found for email: " + email));
    }

    private AppUserPrincipal toPrincipal(PlatformAdmin admin) {
        return new AppUserPrincipal(admin.getId(), admin.getEmail(), admin.getPasswordHash(), null, "ROLE_PLATFORM_ADMIN");
    }

    private AppUserPrincipal toPrincipal(TenantAdmin admin) {
        return new AppUserPrincipal(admin.getId(), admin.getEmail(), admin.getPasswordHash(), admin.getTenant().getId(), "ROLE_TENANT_ADMIN");
    }
}