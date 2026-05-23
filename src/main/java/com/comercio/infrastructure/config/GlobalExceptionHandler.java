package com.comercio.infrastructure.config;

import com.comercio.domain.exceptions.BusinessRuleValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleValidationException.class)
    public ResponseEntity<?> handleBusinessException(BusinessRuleValidationException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "orderId", ex.getOrderId(),
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of(
                "error", ex.getMessage()
        ));
    }
}
