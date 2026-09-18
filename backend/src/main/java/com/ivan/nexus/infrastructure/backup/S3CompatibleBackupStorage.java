package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeleton S3-compatible storage. Enabled behind {@code nexus.backup.s3.enabled=true}.
 * Does not perform network I/O yet — logs and delegates existence/delete as no-ops.
 */
public class S3CompatibleBackupStorage implements BackupStorage {
    private static final Logger log = LoggerFactory.getLogger(S3CompatibleBackupStorage.class);

    private final String endpoint;
    private final String bucket;

    public S3CompatibleBackupStorage(String endpoint, String bucket) {
        this.endpoint = endpoint == null ? "" : endpoint;
        this.bucket = bucket == null ? "" : bucket;
    }

    @Override
    public String store(String projectId, String localPath) {
        log.info("S3 backup storage stub: would upload {} for project {} to s3://{}/{} ({})",
                localPath, projectId, bucket, projectId, endpoint);
        return "s3://" + bucket + "/" + projectId + "/" + fileName(localPath);
    }

    @Override
    public boolean exists(String artifactUri) {
        log.debug("S3 backup storage stub: exists({}) -> false", artifactUri);
        return false;
    }

    @Override
    public void delete(String artifactUri) {
        log.info("S3 backup storage stub: would delete {}", artifactUri);
    }

    private static String fileName(String localPath) {
        if (localPath == null) {
            return "artifact";
        }
        int slash = Math.max(localPath.lastIndexOf('/'), localPath.lastIndexOf('\\'));
        return slash >= 0 ? localPath.substring(slash + 1) : localPath;
    }
}
