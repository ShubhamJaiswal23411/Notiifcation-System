package com.example.notificationservice.service.dispatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationDispatchExecutor executor;

    @Async("notificationExecutor")
    public void dispatchAsync(Long notificationId) {
        try {
            executor.dispatch(notificationId);
        } catch (Exception e) {
            // This is now the permanent safety net: any future dispatch failure,
            // whatever the cause, gets logged loudly instead of vanishing silently
            // the way @Async's default exception handling would otherwise let it.
            log.error("Dispatch failed for notification {}", notificationId, e);
        }
    }
}