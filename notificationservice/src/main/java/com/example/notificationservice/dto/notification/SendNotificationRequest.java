package com.example.notificationservice.dto.notification;

import com.example.notificationservice.domain.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
public class SendNotificationRequest {

    @NotNull
    private ChannelType channelType;

    @NotNull
    private Long templateId;

    @NotBlank
    private String recipient;

    private Map<String, String> variables;

    private OffsetDateTime scheduledAt; // null/past = send immediately

    private String idempotencyKey; // optional, generated if not supplied
}