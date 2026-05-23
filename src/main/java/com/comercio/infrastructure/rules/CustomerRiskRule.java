package com.comercio.infrastructure.rules;

import com.comercio.domain.rules.BusinessRule;
import com.comercio.domain.rules.RuleContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@Order(2)
public class CustomerRiskRule implements BusinessRule {

    @Override
    public boolean evaluate(RuleContext context) {
        context.addLog("Evaluando riesgo crediticio del cliente");
        
        if (context.payload().totalAmount().compareTo(new BigDecimal("1000000")) > 0) {
            context.addLog("Fallo: Monto excede el límite de fraude automatizado.");
            return false;
        }
        return true;
    }

    @Override
    public String getRuleName() { return "CUSTOMER_RISK_RULE"; }
}
