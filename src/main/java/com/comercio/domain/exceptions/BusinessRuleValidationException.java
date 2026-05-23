package com.comercio.domain.exceptions;

import java.util.UUID;

public class BusinessRuleValidationException extends RuntimeException {
    private final UUID orderId;

    public BusinessRuleValidationException(UUID orderId, String message) {
        super(message);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
