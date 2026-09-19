package com.example.notificationservice.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String role;
    private Long tenantId; // null for platform admins
    private long expiresInMillis;
}