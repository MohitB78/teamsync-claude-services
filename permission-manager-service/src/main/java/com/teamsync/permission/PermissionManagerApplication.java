package com.teamsync.permission;

import com.teamsync.common.config.BaseSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Permission Manager Service Application.
 *
 * Provides Drive-Level RBAC with Redis-cached O(1) permission checks
 * for petabyte-scale document management.
 *
 * Port: 9096
 */
@SpringBootApplication
@EnableCaching
@ComponentScan(
        basePackages = {"com.teamsync.permission", "com.teamsync.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = BaseSecurityConfig.class
        )
)
@EnableMongoRepositories(basePackages = "com.teamsync.permission.repository")
public class PermissionManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PermissionManagerApplication.class, args);
    }
}
