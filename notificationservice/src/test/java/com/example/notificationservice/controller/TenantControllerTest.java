package com.example.notificationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.dto.tenant.CreateTenantRequest;
import com.example.notificationservice.dto.tenant.TenantResponse;
import com.example.notificationservice.security.AppUserDetailsService;
import com.example.notificationservice.security.JwtAuthFilter;
import com.example.notificationservice.security.JwtService;
import com.example.notificationservice.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(TenantController.class)
@Import(SecurityConfig.class)
public class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;
    
    @MockitoBean 
    private JwtService jwtService;
    
    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void createTenant_platformAdmin_returns201() throws Exception {
        TenantResponse response = new TenantResponse(1L, "Acme", "tnk_abc", null, OffsetDateTime.now());
        when(tenantService.createTenant(any())).thenReturn(response);

        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("Acme");

        mockMvc.perform(post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_TENANT_ADMIN")
    void createTenant_tenantAdmin_returns403() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("Should Fail");

        mockMvc.perform(post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTenant_noAuth_returns401() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("Acme");

        mockMvc.perform(post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void createTenant_blankName_returns400() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("");

        mockMvc.perform(post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PLATFORM_ADMIN")
    void listTenants_returnsList() throws Exception {
        when(tenantService.listTenants()).thenReturn(
                List.of(new TenantResponse(1L, "Acme", "tnk_abc", null, OffsetDateTime.now())));

        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }
}