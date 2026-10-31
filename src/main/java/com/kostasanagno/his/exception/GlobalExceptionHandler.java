package com.kostasanagno.his.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every exception to {@link ApiError}. Spring MVC's own exceptions (malformed JSON, unsupported
 * method, 404 for unknown paths, ...) are covered by {@link ResponseEntityExceptionHandler}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), cause);
        // Safety net for concurrent requests: the exclusion/unique constraints in V1 reject the second write
        if (cause.contains("ex_appointments_doctor_no_overlap")) {
            return build(HttpStatus.CONFLICT, "The doctor is already booked during the requested time", request);
        }
        if (cause.contains("ex_appointments_patient_no_overlap")) {
            return build(HttpStatus.CONFLICT, "The patient is already booked during the requested time", request);
        }
        if (cause.contains("ex_bed_assignments_bed_no_overlap")) {
            return build(HttpStatus.CONFLICT, "The bed is already occupied", request);
        }
        if (cause.contains("uq_hospitalizations_patient_admitted")) {
            return build(HttpStatus.CONFLICT, "The patient already has an active hospitalization", request);
        }
        if (cause.contains("uq_hospitalizations_appointment")) {
            return build(HttpStatus.CONFLICT, "The appointment is already linked to a hospitalization", request);
        }
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '%s'".formatted(ex.getName()), request);
    }

    /** E.g. {@code ?sort=doesNotExist}. */
    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ApiError> handleUnknownProperty(PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Unknown property '%s'".formatted(ex.getPropertyName()), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    /** Thrown by {@code @PreAuthorize}; without this the catch-all below would turn it into a 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldViolation(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(status).body(new ApiError(java.time.Instant.now(), status.value(),
                reason(status), "Validation failed", path(request), violations));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String message = statusCode.is5xxServerError() ? "Unexpected server error"
                : (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException
                        ? "Malformed or unreadable request body" : ex.getMessage());
        ApiError error = ApiError.of(statusCode.value(), reason(statusCode), message, path(request));
        return ResponseEntity.status(statusCode).headers(headers).body(error);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, null);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
            List<ApiError.FieldViolation> violations) {
        ApiError body = new ApiError(java.time.Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : null;
    }

    private static String reason(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    }
}
