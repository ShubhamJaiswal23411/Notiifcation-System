package com.example.notificationservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private AppUserDetailsService userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_skipsAuthenticationAndContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).isTokenValid(any());
    }

    @Test
    void headerWithoutBearerPrefix_skipsAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic somecreds");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).isTokenValid(any());
    }

    @Test
    void validToken_setsAuthenticationInContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
        when(jwtService.isTokenValid("valid.token.here")).thenReturn(true);
        when(jwtService.extractEmail("valid.token.here")).thenReturn("admin@acme.com");
        UserDetails userDetails = new User("admin@acme.com", "hashed",
                Collections.singletonList(() -> "ROLE_TENANT_ADMIN"));
        when(userDetailsService.loadUserByUsername("admin@acme.com")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        org.assertj.core.api.Assertions.assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_doesNotSetAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
        when(jwtService.isTokenValid("bad.token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        org.assertj.core.api.Assertions.assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(filterChain).doFilter(request, response);
    }
}