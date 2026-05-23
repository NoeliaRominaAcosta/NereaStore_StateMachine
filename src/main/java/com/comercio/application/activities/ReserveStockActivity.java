package com.comercio.application.activities;

import com.comercio.application.dto.PurchaseEventPayload;
import com.comercio.domain.exceptions.BusinessRuleValidationException;
import com.comercio.domain.rules.OrderRuleEngine;
import com.comercio.domain.rules.RuleContext;
import org.springframework.stereotype.Component;

@Component
public class ReserveStockActivity {

    private final OrderRuleEngine ruleEngine;

    public ReserveStockActivity(OrderRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public void execute(PurchaseEventPayload payload) {
        RuleContext context = new RuleContext(payload, new StringBuilder("Iniciando procesamiento: "));

        OrderRuleEngine.RuleResult result = ruleEngine.processRules(context);

        if (!result.isApproved()) {
            throw new BusinessRuleValidationException(payload.orderId(), "La orden no cumple las condiciones: " + result.details());
        }

        System.out.println("LOG ACTIVITY: Stock reservado con éxito para la orden " + payload.orderId());
    }
}
