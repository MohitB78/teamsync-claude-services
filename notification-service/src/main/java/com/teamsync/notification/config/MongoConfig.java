package com.teamsync.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for the Notification Service.
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.teamsync.notification.repository")
public class MongoConfig {
    // Uses DynamicMongoConfig from teamsync-common for Vault integration
    // Database: teamsync (shared)
    // Collections: notifications, notification_preferences
}
