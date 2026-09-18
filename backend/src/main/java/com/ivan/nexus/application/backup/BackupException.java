package com.ivan.nexus.application.backup;

/**
 * Raised when a backup dump cannot be completed.
 */
public class BackupException extends RuntimeException {
    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
