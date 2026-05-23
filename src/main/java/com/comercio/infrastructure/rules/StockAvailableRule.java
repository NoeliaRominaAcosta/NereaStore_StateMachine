package com.comercio.infrastructure.rules;

import com.comercio.domain.rules.BusinessRule;
import com.comercio.domain.rules.RuleContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class StockAvailableRule implements BusinessRule {

    @Override
    public boolean evaluate(RuleContext context) {
        context.addLog("Evaluando disponibilidad de Stock");
        
        boolean allItemsAvailable = context.payload().items().stream()
                .allMatch(item -> item.quantity() < 100); 

        if (!allItemsAvailable) {
            context.addLog("Fallo: Uno o más productos no tienen stock suficiente.");
            return false;
        }
        return true;
    }

    @Override
    public String getRuleName() { return "STOCK_AVAILABILITY_RULE"; }
}
