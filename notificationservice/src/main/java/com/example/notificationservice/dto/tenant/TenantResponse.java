package com.example.notificationservice.dto.tenant;

import com.example.notificationservice.domain.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class TenantResponse {
    private Long id;
    private String name;
    private String apiKey;
    private TenantStatus status;
    private OffsetDateTime createdAt;
}