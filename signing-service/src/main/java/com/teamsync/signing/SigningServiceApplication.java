package com.teamsync.signing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.teamsync.signing", "com.teamsync.common"})
public class SigningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigningServiceApplication.class, args);
    }
}
