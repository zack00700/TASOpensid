package fr.alb.dto;

import java.util.Map;

public class ErrorResponse {
        private String error;
        private String message;
        private int status;
        private Map<String, Object> details;

        public ErrorResponse() {}

        /**
         * Standard constructor used by most resources and GlobalExceptionMapper.
         */
        public ErrorResponse(String error, String message, int status) {
                this.error = error;
                this.message = message;
                this.status = status;
        }

        /**
         * Extended constructor that also carries structured details (used by TaxResource,
         * TaxCalculationResource, etc.).
         */
        public ErrorResponse(String error, String message, int status, Map<String, Object> details) {
                this.error = error;
                this.message = message;
                this.status = status;
                this.details = details;
        }

        public String getError() {
                return error;
        }

        public void setError(String error) {
                this.error = error;
        }

        public String getMessage() {
                return message;
        }

        public void setMessage(String message) {
                this.message = message;
        }

        public int getStatus() {
                return status;
        }

        public void setStatus(int status) {
                this.status = status;
        }

        public Map<String, Object> getDetails() {
                return details;
        }

        public void setDetails(Map<String, Object> details) {
                this.details = details;
        }
}
