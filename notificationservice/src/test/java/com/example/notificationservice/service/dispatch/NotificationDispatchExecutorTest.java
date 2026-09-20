package com.example.notificationservice.service.dispatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.Template;
import com.example.notificationservice.domain.Tenant;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.repository.DeliveryAttemptRepository;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.repository.RateLimitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class NotificationDispatchExecutorTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private RateLimitRepository rateLimitRepository;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private RetryBackoffCalculator backoffCalculator;
    @Mock
    private TemplateRenderer templateRenderer;
    @Mock
    private MockNotificationProvider provider;

    private NotificationDispatchExecutor executor;

    private Notification notification;
    private Tenant tenant;
    private Template template;

    @BeforeEach
    void setUp() {
        executor = new NotificationDispatchExecutor(
                notificationRepository, deliveryAttemptRepository, rateLimitRepository,
                rateLimiterService, backoffCalculator, templateRenderer, provider, new ObjectMapper());

        tenant = new Tenant();
        tenant.setId(1L);

        template = new Template();
        template.setSubject("Subject");
        template.setBody("Body {{name}}");

        notification = new Notification();
        notification.setId(1L);
        notification.setTenant(tenant);
        notification.setChannelType(ChannelType.EMAIL);
        notification.setTemplate(template);
        notification.setRecipient("test@example.com");
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setAttemptCount(0);
        notification.setVariablesJson("{}");
    }

    @Test
    void dispatch_notificationNotFound_doesNothing() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        executor.dispatch(999L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void dispatch_alreadySent_isSkipped() {
        notification.setStatus(NotificationStatus.SENT);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        executor.dispatch(1L);

        verify(notificationRepository, never()).save(any());
        verify(provider, never()).send(any(), any(), any(), any());
    }

    @Test
    void dispatch_rateLimitExceeded_marksFailedWithShortRetry() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(rateLimitRepository.findByTenantIdAndChannelType(1L, ChannelType.EMAIL)).thenReturn(Optional.empty());
        when(rateLimiterService.tryConsume(anyString(), eq(60))).thenReturn(false);

        executor.dispatch(1L);

        verify(notificationRepository).save(argThat(n -> n.getStatus() == NotificationStatus.FAILED));
        verify(provider, never()).send(any(), any(), any(), any());
    }

    @Test
    void dispatch_successfulSend_marksSent() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(rateLimitRepository.findByTenantIdAndChannelType(any(), any())).thenReturn(Optional.empty());
        when(rateLimiterService.tryConsume(anyString(), anyInt())).thenReturn(true);
        when(templateRenderer.render(any(), any())).thenReturn("rendered");
        when(provider.send(any(), any(), any(), any())).thenReturn(true);

        executor.dispatch(1L);

        verify(notificationRepository, times(2)).save(any(Notification.class)); // SENDING, then SENT
        verify(deliveryAttemptRepository).save(argThat(a -> a.getStatus().name().equals("SUCCESS")));
    }

    @Test
    void dispatch_failedSendWithAttemptsRemaining_schedulesRetry() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(rateLimitRepository.findByTenantIdAndChannelType(any(), any())).thenReturn(Optional.empty());
        when(rateLimiterService.tryConsume(anyString(), anyInt())).thenReturn(true);
        when(templateRenderer.render(any(), any())).thenReturn("rendered");
        when(provider.send(any(), any(), any(), any())).thenReturn(false);
        when(backoffCalculator.hasAttemptsRemaining(1)).thenReturn(true);
        when(backoffCalculator.nextDelay(1)).thenReturn(java.time.Duration.ofSeconds(30));

        executor.dispatch(1L);

        verify(notificationRepository, atLeastOnce())
                .save(argThat(n -> n.getStatus() == NotificationStatus.FAILED && n.getNextRetryAt() != null));
    }

    @Test
    void dispatch_failedSendMaxAttemptsReached_deadLetters() {
        notification.setAttemptCount(4); // this attempt becomes #5
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(rateLimitRepository.findByTenantIdAndChannelType(any(), any())).thenReturn(Optional.empty());
        when(rateLimiterService.tryConsume(anyString(), anyInt())).thenReturn(true);
        when(templateRenderer.render(any(), any())).thenReturn("rendered");
        when(provider.send(any(), any(), any(), any())).thenReturn(false);
        when(backoffCalculator.hasAttemptsRemaining(5)).thenReturn(false);

        executor.dispatch(1L);

        verify(notificationRepository, atLeastOnce())
                .save(argThat(n -> n.getStatus() == NotificationStatus.DEAD_LETTER));
    }
}