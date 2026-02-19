package com.teamsync.content;

import com.teamsync.common.config.BaseSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Content Service Application.
 *
 * Note: BaseSecurityConfig is excluded because this service has its own SecurityConfig.
 * Both configs match "any request" which would cause a conflict.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.teamsync.content", "com.teamsync.common"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = BaseSecurityConfig.class
    )
)
@EnableScheduling
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
