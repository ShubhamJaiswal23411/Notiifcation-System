package com.example.notificationservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.notificationservice.dto.channel.ChannelResponse;
import com.example.notificationservice.dto.channel.CreateChannelRequest;
import com.example.notificationservice.security.CurrentUser;
import com.example.notificationservice.service.ChannelService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    public ResponseEntity<ChannelResponse> createChannel(@Valid @RequestBody CreateChannelRequest request) {
        Long tenantId = CurrentUser.tenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(channelService.createChannel(tenantId, request));
    }

    @GetMapping
    public ResponseEntity<List<ChannelResponse>> listChannels() {
        return ResponseEntity.ok(channelService.listChannels(CurrentUser.tenantId()));
    }
}