package com.example.notificationservice.service;

import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.TenantAdmin;
import com.example.notificationservice.domain.enums.TenantStatus;
import com.example.notificationservice.dto.tenant.*;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.TenantAdminRepository;
import com.example.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantAdminRepository tenantAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setApiKey(generateApiKey());
        tenant.setStatus(TenantStatus.ACTIVE);
        Tenant saved = tenantRepository.save(tenant);
        return toResponse(saved);
    }

    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TenantResponse getTenant(Long id) {
        return toResponse(findTenantOrThrow(id));
    }

    @Transactional
    public TenantAdminResponse createTenantAdmin(Long tenantId, CreateTenantAdminRequest request) {
        Tenant tenant = findTenantOrThrow(tenantId);

        TenantAdmin admin = new TenantAdmin();
        admin.setTenant(tenant);
        admin.setEmail(request.getEmail());
        admin.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        TenantAdmin saved = tenantAdminRepository.save(admin);
        return new TenantAdminResponse(saved.getId(), saved.getEmail(), tenant.getId());
    }

    private Tenant findTenantOrThrow(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    private String generateApiKey() {
        return "tnk_" + UUID.randomUUID().toString().replace("-", "");
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getApiKey(),
                tenant.getStatus(), tenant.getCreatedAt());
    }
}