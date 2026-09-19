package com.example.notificationservice.controller;

import com.example.notificationservice.dto.ratelimit.*;
import com.example.notificationservice.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/rate-limits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class RateLimitController {

    private final RateLimitService rateLimitService;

    @PutMapping
    public ResponseEntity<RateLimitResponse> setRateLimit(
            @PathVariable Long tenantId, @Valid @RequestBody SetRateLimitRequest request) {
        return ResponseEntity.ok(rateLimitService.setRateLimit(tenantId, request));
    }
}