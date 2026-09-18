package com.example.notificationservice.repository;

import com.example.notificationservice.domain.TenantAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantAdminRepository extends JpaRepository<TenantAdmin, Long> {
    Optional<TenantAdmin> findByEmail(String email);
}
