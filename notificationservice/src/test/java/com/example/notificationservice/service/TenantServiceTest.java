package com.example.notificationservice.service;

import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.TenantAdmin;
import com.example.notificationservice.domain.enums.TenantStatus;
import com.example.notificationservice.dto.tenant.*;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.TenantAdminRepository;
import com.example.notificationservice.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantAdminRepository tenantAdminRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private TenantService tenantService;

    private Tenant sampleTenant;

    @BeforeEach
    void setUp() {
        sampleTenant = new Tenant();
        sampleTenant.setId(1L);
        sampleTenant.setName("Acme");
        sampleTenant.setApiKey("tnk_abc123");
        sampleTenant.setStatus(TenantStatus.ACTIVE);
        sampleTenant.setCreatedAt(OffsetDateTime.now());
    }

    @Test
    void createTenant_savesAndReturnsResponse() {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("Acme");
        when(tenantRepository.save(any(Tenant.class))).thenReturn(sampleTenant);

        TenantResponse response = tenantService.createTenant(request);

        assertThat(response.getName()).isEqualTo("Acme");
        assertThat(response.getApiKey()).startsWith("tnk_");
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void listTenants_returnsAllMapped() {
        when(tenantRepository.findAll()).thenReturn(List.of(sampleTenant));

        List<TenantResponse> result = tenantService.listTenants();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getTenant_found_returnsResponse() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(sampleTenant));

        TenantResponse response = tenantService.getTenant(1L);

        assertThat(response.getName()).isEqualTo("Acme");
    }

    @Test
    void getTenant_notFound_throws() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createTenantAdmin_tenantExists_savesAdmin() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(sampleTenant));
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        TenantAdmin savedAdmin = new TenantAdmin();
        savedAdmin.setId(5L);
        savedAdmin.setTenant(sampleTenant);
        savedAdmin.setEmail("admin@acme.com");
        when(tenantAdminRepository.save(any(TenantAdmin.class))).thenReturn(savedAdmin);

        CreateTenantAdminRequest request = new CreateTenantAdminRequest();
        request.setEmail("admin@acme.com");
        request.setPassword("pass123");

        TenantAdminResponse response = tenantService.createTenantAdmin(1L, request);

        assertThat(response.getEmail()).isEqualTo("admin@acme.com");
        assertThat(response.getTenantId()).isEqualTo(1L);
        verify(passwordEncoder).encode("pass123");
    }

    @Test
    void createTenantAdmin_tenantNotFound_throws() {
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        CreateTenantAdminRequest request = new CreateTenantAdminRequest();
        request.setEmail("x@x.com");
        request.setPassword("x");

        assertThatThrownBy(() -> tenantService.createTenantAdmin(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}