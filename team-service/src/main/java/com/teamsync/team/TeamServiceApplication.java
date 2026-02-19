package com.teamsync.team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.teamsync.team", "com.teamsync.common"})
public class TeamServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamServiceApplication.class, args);
    }
}
