package com.teamsync.presence;

import com.teamsync.common.config.BaseSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
@ComponentScan(
        basePackages = {"com.teamsync.presence", "com.teamsync.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = BaseSecurityConfig.class
        )
)
public class PresenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PresenceServiceApplication.class, args);
    }
}
