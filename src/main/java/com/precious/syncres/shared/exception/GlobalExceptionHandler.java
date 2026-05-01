package com.precious.syncres.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case EMAIL_ALREADY_REGISTERED -> HttpStatus.CONFLICT;
            case EMAIL_NOT_VERIFIED, JOB_ACCESS_DENIED, DOWNLOAD_INVALID_SIGNATURE -> HttpStatus.FORBIDDEN;
            case OTP_INVALID, OTP_EXPIRED, INVALID_STATUS_TRANSITION, STATUS_IS_TERMINAL -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CV_NOT_FOUND, APPLICATION_NOT_FOUND, JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DOWNLOAD_EXPIRED -> HttpStatus.GONE;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status).body(Map.of(
                "error", ex.getErrorCode(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", ErrorCode.INTERNAL_SERVER_ERROR,
                "message", "An unexpected error occurred"
        ));
    }
}
