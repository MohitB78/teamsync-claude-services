package com.teamsync.sharing;

import com.teamsync.common.config.BaseSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = {"com.teamsync.sharing", "com.teamsync.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = BaseSecurityConfig.class
        )
)
public class SharingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharingServiceApplication.class, args);
    }
}
