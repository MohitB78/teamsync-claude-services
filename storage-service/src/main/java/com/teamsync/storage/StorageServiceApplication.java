package com.teamsync.storage;

import com.teamsync.common.config.BaseSecurityConfig;
import com.teamsync.common.config.MinioConfig;
import com.teamsync.common.storage.MinIOStorageProvider;
import com.teamsync.common.util.DownloadTokenUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Storage Service Application.
 *
 * Exclusions:
 * - BaseSecurityConfig: This service has its own SecurityConfig that matches "any request"
 * - DownloadTokenUtil: Not used by this service (content-service handles download tokens)
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.teamsync.storage", "com.teamsync.common"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {BaseSecurityConfig.class, DownloadTokenUtil.class}
    )
)
@Import({MinioConfig.class, MinIOStorageProvider.class})
public class StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
