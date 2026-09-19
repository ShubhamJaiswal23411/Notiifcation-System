package com.example.notificationservice.dto.notification;

import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.domain.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private ChannelType channelType;
    private String recipient;
    private NotificationStatus status;
    private OffsetDateTime scheduledAt;
    private int attemptCount;
    private OffsetDateTime createdAt;
}