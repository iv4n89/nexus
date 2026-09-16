package com.ivan.nexus.interfaces.error;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        return envelope(statusFor(ex.getCode()), ex.getCode().name(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied() {
        return envelope(HttpStatus.FORBIDDEN, NexusErrorCode.FORBIDDEN.name(), "Forbidden");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials() {
        return envelope(HttpStatus.UNAUTHORIZED, NexusErrorCode.AUTH_INVALID.name(), "Invalid credentials");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error");
    }

    private static ResponseEntity<ErrorResponse> envelope(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(new ErrorResponse.ErrorBody(code, message, Instant.now())));
    }

    private static HttpStatus statusFor(NexusErrorCode code) {
        return switch (code) {
            case PROJECT_NOT_FOUND, CONTAINER_NOT_FOUND, DEPLOYMENT_NOT_FOUND, ALERT_NOT_FOUND, MANIFEST_NOT_FOUND
                    -> HttpStatus.NOT_FOUND;
            case DEPLOYMENT_IN_PROGRESS, INVALID_TRANSITION -> HttpStatus.CONFLICT;
            case MANIFEST_INVALID, OPERATION_NOT_ALLOWED, AUTH_INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
    }
}
