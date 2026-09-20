package com.example.notificationservice.controller;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.domain.enums.ChannelType;
import com.example.notificationservice.dto.template.CreateTemplateRequest;
import com.example.notificationservice.dto.template.TemplateResponse;
import com.example.notificationservice.security.AppUserDetailsService;
import com.example.notificationservice.security.JwtService;
import com.example.notificationservice.service.TemplateService;
import com.example.notificationservice.support.TestAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@Import(SecurityConfig.class)
public class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private TemplateService templateService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    @Test
    void createTemplate_tenantAdmin_returns201() throws Exception {
        TemplateResponse response = new TemplateResponse(1L, ChannelType.EMAIL, "welcome", "Hi", "Hi {{name}}", 1);
        when(templateService.createTemplate(eq(1L), any())).thenReturn(response);

        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setName("welcome");
        request.setBody("Hi {{name}}");

        mockMvc.perform(post("/api/templates")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("welcome"));
    }

    @Test
    void createTemplate_blankBody_returns400() throws Exception {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setName("welcome");
        request.setBody("");

        mockMvc.perform(post("/api/templates")
                        .with(authentication(TestAuth.tenantAdmin(1L, "admin@acme.com", 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void createTemplate_platformAdmin_returns403() throws Exception {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setChannelType(ChannelType.EMAIL);
        request.setName("x");
        request.setBody("x");

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTemplates_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isUnauthorized());
    }
}