package com.example.notificationservice.security;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;

@Getter
public class AppUserPrincipal extends User {

    private final Long id;
    private final Long tenantId; // null for platform admins

    public AppUserPrincipal(Long id, String email, String passwordHash, Long tenantId, String role) {
        super(email, passwordHash, Collections.singletonList(new SimpleGrantedAuthority(role)));
        this.id = id;
        this.tenantId = tenantId;
    }
}