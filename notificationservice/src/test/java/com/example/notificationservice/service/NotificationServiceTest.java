package com.example.notificationservice.service;

import com.example.notificationservice.domain.*;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.dto.notification.SendNotificationRequest;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.*;
import com.example.notificationservice.service.dispatch.NotificationDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private NotificationDispatchService dispatchService;
    @Mock private DeliveryAttemptRepository deliveryAttemptRepository;
    
    @InjectMocks private NotificationService notificationService;

    /*
    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final TenantRepository tenantRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final DeliveryAttemptRepository deliveryAttemptRepository; 
    */

    private Tenant tenant;
    private Template template;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper - cheap to construct, avoids mocking JSON (de)serialization
        notificationService = new NotificationService(
                notificationRepository, templateRepository, tenantRepository,
                dispatchService, new ObjectMapper(), deliveryAttemptRepository);

        tenant = new Tenant();
        tenant.setId(1L);

        template = new Template();
        template.setId(10L);
        template.setTenant(tenant);
        template.setChannelType(ChannelType.EMAIL);
    }

    private SendNotificationRequest buildRequest() {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setChannelType(ChannelType.EMAIL);
        req.setTemplateId(10L);
        req.setRecipient("test@example.com");
        req.setVariables(Map.of("name", "Alex"));
        return req;
    }

    @Test
    void createNotification_immediate_queuesAndDispatches() {
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });

        var response = notificationService.createNotification(1L, buildRequest());

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        verify(dispatchService).dispatchAsync(100L);
    }

    @Test
    void createNotification_futureScheduled_doesNotDispatchImmediately() {
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        SendNotificationRequest req = buildRequest();
        req.setScheduledAt(OffsetDateTime.now().plusHours(1));

        var response = notificationService.createNotification(1L, req);

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
        verify(dispatchService, never()).dispatchAsync(any());
    }

    @Test
    void createNotification_duplicateIdempotencyKey_returnsExistingWithoutSaving() {
        Notification existing = new Notification();
        existing.setId(55L);
        existing.setTenant(tenant);
        existing.setChannelType(ChannelType.EMAIL);
        existing.setStatus(NotificationStatus.SENT);
        existing.setCreatedAt(OffsetDateTime.now());

        SendNotificationRequest req = buildRequest();
        req.setIdempotencyKey("fixed-key-123");
        when(notificationRepository.findByIdempotencyKey("fixed-key-123")).thenReturn(Optional.of(existing));

        var response = notificationService.createNotification(1L, req);

        assertThat(response.getId()).isEqualTo(55L);
        verify(notificationRepository, never()).save(any());
        verify(dispatchService, never()).dispatchAsync(any());
    }

    @Test
    void createNotification_tenantNotFound_throws() {
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.createNotification(99L, buildRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createNotification_templateNotFound_throws() {
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(templateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.createNotification(1L, buildRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createNotification_templateBelongsToDifferentTenant_throws() {
        Tenant otherTenant = new Tenant();
        otherTenant.setId(2L);
        template.setTenant(otherTenant);

        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> notificationService.createNotification(1L, buildRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getNotification_wrongTenant_throwsNotFound() {
        Tenant otherTenant = new Tenant();
        otherTenant.setId(2L);
        Notification n = new Notification();
        n.setId(1L);
        n.setTenant(otherTenant);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.getNotification(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}