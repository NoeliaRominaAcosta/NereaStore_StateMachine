package com.comercio.domain.rules;

import com.comercio.application.dto.PurchaseEventPayload;

public record RuleContext(
    PurchaseEventPayload payload,
    StringBuilder evaluationDetails
) {
    public void addLog(String message) {
        evaluationDetails.append(message).append(" | ");
    }
}
