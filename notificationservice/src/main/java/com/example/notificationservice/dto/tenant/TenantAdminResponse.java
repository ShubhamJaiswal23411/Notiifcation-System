package com.example.notificationservice.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TenantAdminResponse {
    private Long id;
    private String email;
    private Long tenantId;
}
