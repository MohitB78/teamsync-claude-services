package com.teamsync.trash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.teamsync.trash", "com.teamsync.common"})
@EnableScheduling
public class TrashServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrashServiceApplication.class, args);
    }
}
