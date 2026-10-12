package com.finflow.core.exception;

import com.finflow.core.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ProblemDetailResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
        log.warn("Handled business exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        String title = ex.getStatus() instanceof HttpStatus httpStatus
                ? httpStatus.getReasonPhrase()
                : (ex.getStatus().value() == 449 ? "Retry With" : "Error");

        ProblemDetailResponse response = ProblemDetailResponse.builder()
                .type("https://finflow.internal/errors/" + ex.getErrorCode().toLowerCase().replace("_", "-"))
                .title(title)
                .status(ex.getStatus().value())
                .detail(ex.getMessage())
                .code(ex.getErrorCode())
                .instance(request.getRequestURI())
                .correlationId(resolveCorrelationId())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemDetailResponse.ValidationErrorItem> errorList = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errorList.add(new ProblemDetailResponse.ValidationErrorItem(
                    fe.getField(),
                    fe.getRejectedValue(),
                    fe.getDefaultMessage()
            ));
        }

        ProblemDetailResponse response = ProblemDetailResponse.builder()
                .type("https://finflow.internal/errors/validation-error")
                .title("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("One or more validation constraints failed on the request body")
                .code("VALIDATION_FAILED")
                .instance(request.getRequestURI())
                .correlationId(resolveCorrelationId())
                .timestamp(Instant.now())
                .errors(errorList)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetailResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        ProblemDetailResponse response = ProblemDetailResponse.builder()
                .type("https://finflow.internal/errors/not-found")
                .title("Not Found")
                .status(HttpStatus.NOT_FOUND.value())
                .detail(ex.getMessage())
                .code("ENDPOINT_NOT_FOUND")
                .instance(request.getRequestURI())
                .correlationId(resolveCorrelationId())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled unexpected internal server exception on {}", request.getRequestURI(), ex);
        ProblemDetailResponse response = ProblemDetailResponse.builder()
                .type("https://finflow.internal/errors/internal-server-error")
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("An unexpected error occurred. Please reference the correlation ID when reporting this issue.")
                .code("INTERNAL_SERVER_ERROR")
                .instance(request.getRequestURI())
                .correlationId(resolveCorrelationId())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.internalServerError().body(response);
    }

    private String resolveCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
