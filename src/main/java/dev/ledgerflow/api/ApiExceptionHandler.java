package dev.ledgerflow.api;

import dev.ledgerflow.domain.LedgerException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Maps failures to RFC 9457 problem details. Every ledger problem has a stable {@code type} URN that clients can
 * branch on; {@code retryable} tells them whether resending the same request may succeed.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:ledgerflow:problem:";

    @ExceptionHandler(LedgerException.class)
    ProblemDetail handleLedger(LedgerException e) {
        HttpStatus status = switch (e) {
            case LedgerException.NotFound ignored -> HttpStatus.NOT_FOUND;
            case LedgerException.Conflict ignored -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problem(status, e.problemType(), e.getMessage(), false);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail handleConcurrency(RuntimeException e) {
        log.info("Concurrent modification: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "concurrent-modification",
                "the resource was modified concurrently, retry the request", true);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        log.info("Integrity violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "integrity-violation",
                "the request conflicts with existing data", false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "request validation failed",
                false);
        problem.setProperty("errors", e.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "request validation failed",
                false);
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "request validation failed",
                false);
        problem.setProperty("errors", ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> Map.of(
                        "field", r.getMethodParameter().getParameterName() != null
                                ? r.getMethodParameter().getParameterName() : "parameter",
                        "message", String.valueOf(err.getDefaultMessage()))))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getType().equals(URI.create("about:blank"))) {
            problem.setType(URI.create(TYPE_PREFIX + "http-" + statusCode.value()));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail, boolean retryable) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("retryable", retryable);
        return problem;
    }
}
