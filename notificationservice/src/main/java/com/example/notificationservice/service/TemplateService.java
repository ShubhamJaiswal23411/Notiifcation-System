package com.example.notificationservice.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.notificationservice.domain.Template;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.dto.template.CreateTemplateRequest;
import com.example.notificationservice.dto.template.TemplateResponse;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.TemplateRepository;
import com.example.notificationservice.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public TemplateResponse createTemplate(Long tenantId, CreateTemplateRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        Template template = new Template();
        template.setTenant(tenant);
        template.setChannelType(request.getChannelType());
        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        template.setVersion(1);

        Template saved = templateRepository.save(template);
        return toResponse(saved);
    }

    public List<TemplateResponse> listTemplates(Long tenantId) {
        return templateRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private TemplateResponse toResponse(Template t) {
        return new TemplateResponse(t.getId(), t.getChannelType(), t.getName(), t.getSubject(), t.getBody(), t.getVersion());
    }
}