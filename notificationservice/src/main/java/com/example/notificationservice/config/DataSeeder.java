package com.example.notificationservice.config;

import com.example.notificationservice.domain.PlatformAdmin;
import com.example.notificationservice.repository.PlatformAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email:admin@notify.local}")
    private String seedEmail;

    @Value("${app.seed.admin-password:ChangeMe123!}")
    private String seedPassword;

    @Override
    public void run(String... args) {
        if (platformAdminRepository.count() == 0) {
            PlatformAdmin admin = new PlatformAdmin();
            admin.setEmail(seedEmail);
            admin.setPasswordHash(passwordEncoder.encode(seedPassword));
            platformAdminRepository.save(admin);
            log.warn("Seeded platform admin: {} — change credentials for anything beyond local/demo use", seedEmail);
        }
    }
}