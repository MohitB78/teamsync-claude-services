package com.teamsync.settings.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for the Settings Service.
 * Uses shared MongoDB from teamsync-common with auto-index creation.
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.teamsync.settings.repository")
public class MongoConfig {
    // Uses DynamicMongoConfig from teamsync-common for Vault integration
    // Database: teamsync (shared with other services)
    // Collections: user_settings, tenant_settings, drive_settings
}
