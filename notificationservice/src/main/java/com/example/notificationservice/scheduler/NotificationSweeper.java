package com.example.notificationservice.scheduler;

import com.example.notificationservice.domain.Notification;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.dispatch.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSweeper {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatchService dispatchService;

    @Scheduled(fixedDelay = 30000)
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();

        List<Notification> dueScheduled = notificationRepository.findDueScheduled(NotificationStatus.SCHEDULED, now);
        dueScheduled.forEach(n -> dispatchService.dispatchAsync(n.getId()));

        List<Notification> dueRetries = notificationRepository.findDueForRetry(NotificationStatus.FAILED, now);
        dueRetries.forEach(n -> dispatchService.dispatchAsync(n.getId()));

        if (!dueScheduled.isEmpty() || !dueRetries.isEmpty()) {
            log.info("Swept {} scheduled and {} retry-due notifications", dueScheduled.size(), dueRetries.size());
        }
    }
}