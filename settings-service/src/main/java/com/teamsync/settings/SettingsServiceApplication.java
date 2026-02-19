package com.teamsync.settings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Settings Service - Lightweight service for user/tenant settings.
 * Uses teamsync-common-core (no storage dependencies).
 */
@SpringBootApplication(scanBasePackages = {
    "com.teamsync.settings",
    "com.teamsync.common.config",      // BaseSecurityConfig
    "com.teamsync.common.security",    // TenantContextFilter
    "com.teamsync.common.exception"    // GlobalExceptionHandler
})
@EnableCaching
public class SettingsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettingsServiceApplication.class, args);
    }
}
