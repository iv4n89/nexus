package com.ivan.nexus.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public class NexusProperties {
    private final Docker docker = new Docker();
    private final Manifest manifest = new Manifest();
    private final Retention retention = new Retention();
    private final GitHub github = new GitHub();
    private final Secrets secrets = new Secrets();
    private final Terminal terminal = new Terminal();

    public Docker getDocker() {
        return docker;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public Retention getRetention() {
        return retention;
    }

    public GitHub getGithub() {
        return github;
    }

    public Secrets getSecrets() {
        return secrets;
    }

    public Terminal getTerminal() {
        return terminal;
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

    public static class Retention {
        private int activityDays = 30;
        private int deploymentEventsDays = 30;
        private int fingerprintDays = 90;
        private String cron = "0 0 3 * * *";

        public int getActivityDays() {
            return activityDays;
        }

        public void setActivityDays(int activityDays) {
            this.activityDays = activityDays;
        }

        public int getDeploymentEventsDays() {
            return deploymentEventsDays;
        }

        public void setDeploymentEventsDays(int deploymentEventsDays) {
            this.deploymentEventsDays = deploymentEventsDays;
        }

        public int getFingerprintDays() {
            return fingerprintDays;
        }

        public void setFingerprintDays(int fingerprintDays) {
            this.fingerprintDays = fingerprintDays;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class GitHub {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }

    public static class Secrets {
        private String key = "";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class Terminal {
        private boolean enabled = false;
        private int sessionTimeoutMinutes = 15;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSessionTimeoutMinutes() {
            return sessionTimeoutMinutes;
        }

        public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
            this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        }
    }
}
