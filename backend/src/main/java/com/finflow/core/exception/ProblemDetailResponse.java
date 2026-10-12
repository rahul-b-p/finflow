package com.finflow.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String code;
    private String correlationId;
    private Instant timestamp;
    private List<ValidationErrorItem> errors;

    public ProblemDetailResponse() {
        this.timestamp = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ProblemDetailResponse response = new ProblemDetailResponse();

        public Builder type(String type) {
            response.type = type;
            return this;
        }

        public Builder title(String title) {
            response.title = title;
            return this;
        }

        public Builder status(int status) {
            response.status = status;
            return this;
        }

        public Builder detail(String detail) {
            response.detail = detail;
            return this;
        }

        public Builder instance(String instance) {
            response.instance = instance;
            return this;
        }

        public Builder code(String code) {
            response.code = code;
            return this;
        }

        public Builder correlationId(String correlationId) {
            response.correlationId = correlationId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            response.timestamp = timestamp;
            return this;
        }

        public Builder errors(List<ValidationErrorItem> errors) {
            response.errors = errors;
            return this;
        }

        public ProblemDetailResponse build() {
            return response;
        }
    }

    public static class ValidationErrorItem {
        private String field;
        private Object rejectedValue;
        private String message;

        public ValidationErrorItem() {
        }

        public ValidationErrorItem(String field, Object rejectedValue, String message) {
            this.field = field;
            this.rejectedValue = rejectedValue;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public Object getRejectedValue() {
            return rejectedValue;
        }

        public void setRejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<ValidationErrorItem> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationErrorItem> errors) {
        this.errors = errors;
    }
}
