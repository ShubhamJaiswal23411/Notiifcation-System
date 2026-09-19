package com.example.notificationservice.service.dispatch;

import com.example.notificationservice.domain.*;
import com.example.notificationservice.domain.enums.DeliveryStatus;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchExecutor {

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final RateLimitRepository rateLimitRepository;
    private final RateLimiterService rateLimiterService;
    private final RetryBackoffCalculator backoffCalculator;
    private final TemplateRenderer templateRenderer;
    private final MockNotificationProvider provider;
    private final ObjectMapper objectMapper;

    @Transactional
    public void dispatch(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            log.warn("Notification {} not found for dispatch", notificationId);
            return;
        }

        if (notification.getStatus() == NotificationStatus.SENT
                || notification.getStatus() == NotificationStatus.DEAD_LETTER) {
            return;
        }

        Long tenantId = notification.getTenant().getId();
        String rateLimitKey = tenantId + ":" + notification.getChannelType();
        int limit = rateLimitRepository.findByTenantIdAndChannelType(tenantId, notification.getChannelType())
                .map(RateLimit::getMaxPerMinute)
                .orElse(60);

        if (!rateLimiterService.tryConsume(rateLimitKey, limit)) {
            log.info("Rate limit hit for {}, deferring notification {}", rateLimitKey, notificationId);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setNextRetryAt(OffsetDateTime.now().plusSeconds(15));
            notificationRepository.save(notification);
            return;
        }

        notification.setStatus(NotificationStatus.SENDING);
        notificationRepository.save(notification);

        Map<String, String> variables = readVariables(notification.getVariablesJson());
        Template template = notification.getTemplate();
        String renderedSubject = templateRenderer.render(template.getSubject(), variables);
        String renderedBody = templateRenderer.render(template.getBody(), variables);

        int attemptNumber = notification.getAttemptCount() + 1;
        boolean success = provider.send(notification.getChannelType(), notification.getRecipient(), renderedSubject, renderedBody);

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setNotification(notification);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(success ? DeliveryStatus.SUCCESS : DeliveryStatus.FAILED);
        attempt.setErrorMessage(success ? null : "Simulated provider failure");
        deliveryAttemptRepository.save(attempt);

        notification.setAttemptCount(attemptNumber);

        if (success) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setNextRetryAt(null);
        } else if (backoffCalculator.hasAttemptsRemaining(attemptNumber)) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setNextRetryAt(OffsetDateTime.now().plus(backoffCalculator.nextDelay(attemptNumber)));
        } else {
            notification.setStatus(NotificationStatus.DEAD_LETTER);
            notification.setNextRetryAt(null);
        }

        notificationRepository.save(notification);
    }

    private Map<String, String> readVariables(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Could not parse variables JSON for notification, using empty map", e);
            return Map.of();
        }
    }
}