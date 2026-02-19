package com.teamsync.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.teamsync.workflow", "com.teamsync.common"})
public class WorkflowExecutionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowExecutionServiceApplication.class, args);
    }
}
