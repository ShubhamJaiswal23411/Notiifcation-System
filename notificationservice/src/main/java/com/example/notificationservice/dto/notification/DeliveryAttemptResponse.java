package com.example.notificationservice.dto.notification;

import com.example.notificationservice.domain.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class DeliveryAttemptResponse {
    private int attemptNumber;
    private DeliveryStatus status;
    private String errorMessage;
    private OffsetDateTime attemptedAt;
}