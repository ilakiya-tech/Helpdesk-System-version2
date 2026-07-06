package com.workflow.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Carbochem Helpdesk Application.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.workflow.engine.config")
public class EnterpriseSlaEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnterpriseSlaEngineApplication.class, args);
    }
}
