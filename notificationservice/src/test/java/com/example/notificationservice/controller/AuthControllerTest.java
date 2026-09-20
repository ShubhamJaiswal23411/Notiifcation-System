package com.example.notificationservice.controller;

import com.example.notificationservice.config.SecurityConfig;
import com.example.notificationservice.dto.auth.LoginRequest;
import com.example.notificationservice.security.AppUserDetailsService;
import com.example.notificationservice.security.AppUserPrincipal;
import com.example.notificationservice.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
public class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        AppUserPrincipal principal = new AppUserPrincipal(1L, "admin@acme.com", "hashed", 1L, "ROLE_TENANT_ADMIN");
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtService.generateToken("admin@acme.com", "ROLE_TENANT_ADMIN", 1L)).thenReturn("fake.jwt.token");

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@acme.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake.jwt.token"))
                .andExpect(jsonPath("$.role").value("ROLE_TENANT_ADMIN"))
                .andExpect(jsonPath("$.tenantId").value(1));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@acme.com");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}