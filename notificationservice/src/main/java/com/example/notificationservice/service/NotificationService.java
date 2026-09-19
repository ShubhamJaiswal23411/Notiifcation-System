package com.example.notificationservice.service;

import com.example.notificationservice.domain.*;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.dto.notification.*;
import com.example.notificationservice.exception.ResourceNotFoundException;
import com.example.notificationservice.repository.*;
import com.example.notificationservice.service.dispatch.NotificationDispatchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final TenantRepository tenantRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationResponse createNotification(Long tenantId, SendNotificationRequest request) {
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        Optional<Notification> existing = notificationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate send suppressed for idempotency key {}", idempotencyKey);
            return toResponse(existing.get());
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        Template template = templateRepository.findById(request.getTemplateId())
                .filter(t -> t.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + request.getTemplateId()));

        Notification notification = new Notification();
        notification.setTenant(tenant);
        notification.setChannelType(request.getChannelType());
        notification.setTemplate(template);
        notification.setRecipient(request.getRecipient());
        notification.setIdempotencyKey(idempotencyKey);
        notification.setVariablesJson(toJson(request.getVariables()));

        boolean isFutureScheduled = request.getScheduledAt() != null
                && request.getScheduledAt().isAfter(OffsetDateTime.now());

        if (isFutureScheduled) {
            notification.setStatus(NotificationStatus.SCHEDULED);
            notification.setScheduledAt(request.getScheduledAt());
        } else {
            notification.setStatus(NotificationStatus.QUEUED);
        }

        Notification saved = notificationRepository.save(notification);

        if (saved.getStatus() == NotificationStatus.QUEUED) {
            dispatchService.dispatchAsync(saved.getId());
        }

        return toResponse(saved);
    }

    public NotificationResponse getNotification(Long tenantId, Long id) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        return toResponse(n);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj == null ? java.util.Map.of() : obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid variables payload");
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getChannelType(), n.getRecipient(),
                n.getStatus(), n.getScheduledAt(), n.getAttemptCount(), n.getCreatedAt());
    }
}