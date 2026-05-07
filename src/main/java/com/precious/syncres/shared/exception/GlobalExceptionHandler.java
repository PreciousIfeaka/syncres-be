package com.precious.syncres.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case EMAIL_ALREADY_REGISTERED -> HttpStatus.CONFLICT;
            case EMAIL_NOT_VERIFIED, JOB_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case OTP_INVALID, OTP_EXPIRED, INVALID_STATUS_TRANSITION, STATUS_IS_TERMINAL, CV_INPUT_REQUIRED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CV_NOT_FOUND, APPLICATION_NOT_FOUND, JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DOWNLOAD_EXPIRED -> HttpStatus.GONE;
            case DOWNLOAD_INVALID_SIGNATURE -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status).body(Map.of(
                "error", ex.getErrorCode(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "VALIDATION_FAILED",
                "fields", errors
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("An unexpected error occurred, {}", ex.toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", ErrorCode.INTERNAL_SERVER_ERROR,
                "message", "An unexpected error occurred"
        ));
    }
}
