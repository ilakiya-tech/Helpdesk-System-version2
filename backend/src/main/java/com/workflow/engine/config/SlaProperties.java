package com.workflow.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "sla")
public class SlaProperties {

    private final Map<String, Rule> rules = new HashMap<>();

    public Map<String, Rule> getRules() {
        return rules;
    }

    public static class Rule {
        private int responseMinutes;
        private int resolutionMinutes;

        public int getResponseMinutes() {
            return responseMinutes;
        }

        public void setResponseMinutes(int responseMinutes) {
            this.responseMinutes = responseMinutes;
        }

        public int getResolutionMinutes() {
            return resolutionMinutes;
        }

        public void setResolutionMinutes(int resolutionMinutes) {
            this.resolutionMinutes = resolutionMinutes;
        }
    }
}
