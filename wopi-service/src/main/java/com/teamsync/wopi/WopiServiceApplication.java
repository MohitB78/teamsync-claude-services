package com.teamsync.wopi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.teamsync.wopi", "com.teamsync.common"})
public class WopiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WopiServiceApplication.class, args);
    }
}
