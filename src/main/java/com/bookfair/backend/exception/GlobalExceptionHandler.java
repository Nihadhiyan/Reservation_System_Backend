package com.bookfair.backend.exception;

import java.util.List;
import java.util.Map;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import com.bookfair.backend.dto.common.ErrorResponse;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(BaseException.class)
        public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
                HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
                ErrorCode errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : ErrorCode.INTERNAL_SERVER_ERROR;

                if (status.is5xxServerError()) {
                        log.error("Server Error [{}]: {}", errorCode, ex.getMessage(), ex);
                } else {
                        log.warn("Business Exception [{}]: {}", errorCode, ex.getMessage());
                }

                return ResponseEntity
                                .status(status)
                                .body(ErrorResponse.build(status, ex.getMessage(), null, errorCode));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

                List<String> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .toList();

                log.warn("Validation failed: {}", errors);

                return ResponseEntity.badRequest()
                                .body(ErrorResponse.build(HttpStatus.BAD_REQUEST, "Validation failed", errors,
                                                ErrorCode.VALIDATION_ERROR));
        }

        @ExceptionHandler({ NullPointerException.class, IllegalArgumentException.class })
        public ResponseEntity<ErrorResponse> handleValidationExceptions(RuntimeException ex) {
                // We log this as a WARN, not an ERROR, because it's a bad client request, not a
                // server failure.
                log.warn("Validation Error (Fail-Fast): {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.build(
                                                HttpStatus.BAD_REQUEST,
                                                ex.getMessage() != null ? ex.getMessage() : "Invalid input provided",
                                                null,
                                                ErrorCode.VALIDATION_ERROR));
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ErrorResponse> handleMissingParams(MissingServletRequestParameterException ex) {
                log.warn("Missing request parameter: {}", ex.getParameterName());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.build(HttpStatus.BAD_REQUEST,
                                                "Missing required parameter: " + ex.getParameterName(), null,
                                                ErrorCode.VALIDATION_ERROR));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {

                List<String> errors = ex.getConstraintViolations()
                                .stream()
                                .map(cs -> cs.getMessage())
                                .toList();

                log.warn("Constraint validation failed: {}", errors);

                return ResponseEntity.badRequest()
                                .body(ErrorResponse.build(HttpStatus.BAD_REQUEST, "Validation failed", errors,
                                                ErrorCode.VALIDATION_ERROR));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {

                log.warn("Authentication failed: {}", ex.getMessage());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ErrorResponse.build(HttpStatus.UNAUTHORIZED, "Authentication failed", null,
                                                ErrorCode.UNAUTHORIZED));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {

                log.warn("Access denied: {}", ex.getMessage());

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.build(HttpStatus.FORBIDDEN, "Access denied", null,
                                                ErrorCode.FORBIDDEN));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {

                log.error("Database constraint violation: {}", ex.getMessage(), ex);

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.build(HttpStatus.CONFLICT, "Database constraint violation", null,
                                                ErrorCode.DATABASE_ERROR));
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {

                log.warn("Illegal state encountered: {}", ex.getMessage());

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.build(HttpStatus.CONFLICT, ex.getMessage(), null,
                                                ErrorCode.BUSINESS_RULE_VIOLATION));
        }

        @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
        public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
                log.warn("Optimistic locking failure: {}", ex.getMessage());

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.build(HttpStatus.CONFLICT,
                                                "The item you are trying to modify was just updated or reserved by someone else. Please refresh the page.",
                                                null,
                                                ErrorCode.RESOURCE_CONFLICT));
        }

        @ExceptionHandler(CannotAcquireLockException.class)
        public ResponseEntity<ErrorResponse> handlePessimisticLockTimeout(CannotAcquireLockException ex) {
                log.warn("Pessimistic lock timeout: {}", ex.getMessage());

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.build(HttpStatus.CONFLICT,
                                                "The system is experiencing extreme demand for this specific item. Please try clicking reserve again.",
                                                null,
                                                ErrorCode.SYSTEM_BUSY));
        }

        @ExceptionHandler(SpaceConflictException.class)
        public ResponseEntity<ErrorResponse> handleSpaceConflict(SpaceConflictException ex) {
                List<Map<String, Object>> details = ex.getConflicts().stream()
                        .<Map<String, Object>>map(c -> Map.of(
                                "level", c.level().name(),
                                "spaceId", c.spaceId(),
                                "spaceName", c.spaceName(),
                                "conflictingEventName", c.conflictingBooking().getEvent().getName(),
                                "conflictStart", c.conflictingBooking().getStartsAt(),
                                "conflictEnd", c.conflictingBooking().getEndsAt()))
                        .toList();

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.build(HttpStatus.CONFLICT,
                                                "Some selected spaces are not available",
                                                details,
                                                ErrorCode.SPACE_CONFLICT));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {

                log.error("CRITICAL: An unexpected error occurred: {}", ex.getMessage(), ex);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.build(HttpStatus.INTERNAL_SERVER_ERROR,
                                                "An unexpected error occurred", null, ErrorCode.INTERNAL_SERVER_ERROR));
        }
}