package com.ivan.nexus.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public class NexusProperties {
    private final Docker docker = new Docker();

    public Docker getDocker() {
        return docker;
    }

    public static class Docker {
        private String host = "unix:///var/run/docker.sock";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }
}
