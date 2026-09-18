package com.ivan.nexus.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public class NexusProperties {
    private final Docker docker = new Docker();
    private final Manifest manifest = new Manifest();
    private final Retention retention = new Retention();
    private final GitHub github = new GitHub();
    private final Secrets secrets = new Secrets();
    private final Backup backup = new Backup();
    private final Security security = new Security();
    private final Terminal terminal = new Terminal();
    private final Caddy caddy = new Caddy();
    private final Automation automation = new Automation();

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

    public Backup getBackup() {
        return backup;
    }

    public Security getSecurity() {
        return security;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public Caddy getCaddy() {
        return caddy;
    }

    public Automation getAutomation() {
        return automation;
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
        private String webhookSecret = "";
        /** When true, runs {@link com.ivan.nexus.application.github.ScheduledGitHubSync}. */
        private boolean syncEnabled;
        /** Every 15 minutes by default (Spring 6-field cron). */
        private String syncCron = "0 */15 * * * *";

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

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public boolean isSyncEnabled() {
            return syncEnabled;
        }

        public void setSyncEnabled(boolean syncEnabled) {
            this.syncEnabled = syncEnabled;
        }

        public String getSyncCron() {
            return syncCron;
        }

        public void setSyncCron(String syncCron) {
            this.syncCron = syncCron;
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

    public static class Backup {
        private boolean enabled;
        private boolean schedulerEnabled;
        private String localPath = "/var/lib/nexus/backups";
        private String pgDumpExecutable = "pg_dump";
        private String cron = "0 0 3 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSchedulerEnabled() {
            return schedulerEnabled;
        }

        public void setSchedulerEnabled(boolean schedulerEnabled) {
            this.schedulerEnabled = schedulerEnabled;
        }

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }

        public String getPgDumpExecutable() {
            return pgDumpExecutable;
        }

        public void setPgDumpExecutable(String pgDumpExecutable) {
            this.pgDumpExecutable = pgDumpExecutable;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class Security {
        private final Trivy trivy = new Trivy();

        public Trivy getTrivy() {
            return trivy;
        }

        public static class Trivy {
            private boolean enabled;
            private boolean schedulerEnabled;
            private String executable = "trivy";
            /** {@code fs} (default) or {@code image}. */
            private String mode = "fs";
            /** Image reference template; {@code {projectId}} is substituted when mode=image. */
            private String imageRef = "";
            private String cron = "0 0 4 * * *";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public boolean isSchedulerEnabled() {
                return schedulerEnabled;
            }

            public void setSchedulerEnabled(boolean schedulerEnabled) {
                this.schedulerEnabled = schedulerEnabled;
            }

            public String getExecutable() {
                return executable;
            }

            public void setExecutable(String executable) {
                this.executable = executable;
            }

            public String getMode() {
                return mode;
            }

            public void setMode(String mode) {
                this.mode = mode;
            }

            public String getImageRef() {
                return imageRef;
            }

            public void setImageRef(String imageRef) {
                this.imageRef = imageRef;
            }

            public String getCron() {
                return cron;
            }

            public void setCron(String cron) {
                this.cron = cron;
            }
        }
    }

    public static class Terminal {
        private boolean enabled = false;
        private int sessionTimeoutMinutes = 15;
        /** Empty = same-host Origin only; use "*" to allow any (not recommended). */
        private java.util.List<String> allowedOrigins = new java.util.ArrayList<>();

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

        public java.util.List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(java.util.List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? new java.util.ArrayList<>() : allowedOrigins;
        }
    }

    public static class Caddy {
        private boolean enabled = false;
        private String sitesPath = "/opt/nexus/caddy/sites";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSitesPath() {
            return sitesPath;
        }

        public void setSitesPath(String sitesPath) {
            this.sitesPath = sitesPath;
        }
    }

    /**
     * Deploy pipeline orchestration flags (H1). All default OFF.
     */
    public static class Automation {
        private boolean securityGateEnabled;
        private boolean trafficWatchEnabled;
        private boolean postDeployBackupEnabled;

        public boolean isSecurityGateEnabled() {
            return securityGateEnabled;
        }

        public void setSecurityGateEnabled(boolean securityGateEnabled) {
            this.securityGateEnabled = securityGateEnabled;
        }

        public boolean isTrafficWatchEnabled() {
            return trafficWatchEnabled;
        }

        public void setTrafficWatchEnabled(boolean trafficWatchEnabled) {
            this.trafficWatchEnabled = trafficWatchEnabled;
        }

        public boolean isPostDeployBackupEnabled() {
            return postDeployBackupEnabled;
        }

        public void setPostDeployBackupEnabled(boolean postDeployBackupEnabled) {
            this.postDeployBackupEnabled = postDeployBackupEnabled;
        }
    }
}
