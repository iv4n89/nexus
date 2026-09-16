package com.ivan.nexus.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public class NexusProperties {
    private final Docker docker = new Docker();
    private final Manifest manifest = new Manifest();

    public Docker getDocker() {
        return docker;
    }

    public Manifest getManifest() {
        return manifest;
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

    public static class Manifest {
        private String allowedRoot = "/home/ibetanzos/dev/nexus_project/projects";

        public String getAllowedRoot() {
            return allowedRoot;
        }

        public void setAllowedRoot(String allowedRoot) {
            this.allowedRoot = allowedRoot;
        }
    }
}
