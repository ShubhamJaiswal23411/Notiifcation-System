package com.example.notificationservice.service;

import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.Template;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.dto.template.CreateTemplateRequest;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.TemplateRepository;
import com.example.notificationservice.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private TemplateService templateService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);
    }

    @Test
    void createTemplate_tenantExists_savesTemplate() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        Template saved = new Template();
        saved.setId(3L);
        saved.setChannelType(ChannelType.EMAIL);
        saved.setName("welcome");
        saved.setBody("Hi {{name}}");
        saved.setVersion(1);
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setName("welcome");
        request.setBody("Hi {{name}}");

        var response = templateService.createTemplate(1L, request);

        assertThat(response.getName()).isEqualTo("welcome");
        assertThat(response.getVersion()).isEqualTo(1);
    }

    @Test
    void createTemplate_tenantNotFound_throws() {
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setChannelType(ChannelType.SMS);
        request.setName("x");
        request.setBody("x");

        assertThatThrownBy(() -> templateService.createTemplate(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listTemplates_returnsMappedList() {
        Template t = new Template();
        t.setId(1L);
        t.setName("order_confirmation");
        t.setChannelType(ChannelType.EMAIL);
        when(templateRepository.findByTenantId(1L)).thenReturn(List.of(t));

        var result = templateService.listTemplates(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("order_confirmation");
    }
}