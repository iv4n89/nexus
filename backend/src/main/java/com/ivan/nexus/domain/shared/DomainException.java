package com.ivan.nexus.domain.shared;

public class DomainException extends RuntimeException {
    private final NexusErrorCode code;

    public DomainException(NexusErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public NexusErrorCode getCode() {
        return code;
    }
}
