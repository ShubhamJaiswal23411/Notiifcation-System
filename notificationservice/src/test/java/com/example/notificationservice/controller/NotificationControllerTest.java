package com.example.notificationservice.controller;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.domain.enums.DeliveryStatus;
import com.example.notificationservice.domain.enums.NotificationStatus;
import com.example.notificationservice.dto.notification.*;
import com.example.notificationservice.security.AppUserDetailsService;
import com.example.notificationservice.security.JwtService;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.support.TestAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
public class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    @Test
    void send_validRequest_returns201() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, ChannelType.EMAIL, "test@example.com", NotificationStatus.QUEUED, null, 0, OffsetDateTime.now());
        when(notificationService.createNotification(eq(1L), any())).thenReturn(response);

        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setTemplateId(10L);
        request.setRecipient("test@example.com");
        request.setVariables(Map.of("name", "Alex"));

        mockMvc.perform(post("/api/notifications")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void send_missingRecipient_returns400() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setTemplateId(10L);
        // recipient intentionally left blank

        mockMvc.perform(post("/api/notifications")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_noAuth_returns401() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setTemplateId(10L);
        request.setRecipient("test@example.com");

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_returnsNotification() throws Exception {
        NotificationResponse response = new NotificationResponse(
                5L, ChannelType.SMS, "recipient", NotificationStatus.SENT, null, 1, OffsetDateTime.now());
        when(notificationService.getNotification(1L, 5L)).thenReturn(response);

        mockMvc.perform(get("/api/notifications/5")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void list_returnsNotifications() throws Exception {
        when(notificationService.listNotifications(1L)).thenReturn(List.of(
                new NotificationResponse(1L, ChannelType.EMAIL, "a@b.com", NotificationStatus.SENT, null, 1, OffsetDateTime.now())));

        mockMvc.perform(get("/api/notifications")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAttempts_returnsAttemptHistory() throws Exception {
        when(notificationService.getAttempts(1L, 5L)).thenReturn(List.of(
                new DeliveryAttemptResponse(1, DeliveryStatus.FAILED, "Simulated provider failure", OffsetDateTime.now()),
                new DeliveryAttemptResponse(2, DeliveryStatus.SUCCESS, null, OffsetDateTime.now())));

        mockMvc.perform(get("/api/notifications/5/attempts")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].status").value("SUCCESS"));
    }
}