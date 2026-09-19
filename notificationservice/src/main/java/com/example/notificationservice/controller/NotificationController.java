package com.example.notificationservice.controller;

import com.example.notificationservice.dto.notification.*;
import com.example.notificationservice.security.CurrentUser;
import com.example.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createNotification(CurrentUser.tenantId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getNotification(CurrentUser.tenantId(), id));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list() {
        return ResponseEntity.ok(notificationService.listNotifications(CurrentUser.tenantId()));
    }

    @GetMapping("/{id}/attempts")
    public ResponseEntity<List<DeliveryAttemptResponse>> getAttempts(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getAttempts(CurrentUser.tenantId(), id));
    }
}