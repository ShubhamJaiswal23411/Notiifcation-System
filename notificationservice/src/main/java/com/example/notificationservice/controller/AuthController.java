package com.example.notificationservice.controller;

import com.example.notificationservice.dto.auth.LoginRequest;
import com.example.notificationservice.dto.auth.LoginResponse;
import com.example.notificationservice.security.AppUserPrincipal;
import com.example.notificationservice.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.jwt.expiration-ms:3600000}")
    private long expirationMillis;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElseThrow();

        String token = jwtService.generateToken(principal.getUsername(), role, principal.getTenantId());

        return ResponseEntity.ok(new LoginResponse(token, role, principal.getTenantId(), expirationMillis));
    }
}