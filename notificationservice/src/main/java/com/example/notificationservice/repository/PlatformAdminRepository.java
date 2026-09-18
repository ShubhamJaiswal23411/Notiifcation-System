package com.example.notificationservice.repository;

import com.example.notificationservice.domain.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {
    Optional<PlatformAdmin> findByEmail(String email);
}