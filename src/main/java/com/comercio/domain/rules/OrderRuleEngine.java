package com.comercio.domain.rules;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrderRuleEngine {

    private final List<BusinessRule> rules;

    // Spring inyecta la lista ordenada si las reglas tienen @Order
    public OrderRuleEngine(List<BusinessRule> rules) {
        this.rules = rules;
    }

    public RuleResult processRules(RuleContext context) {
        for (BusinessRule rule : rules) {
            boolean passed = rule.evaluate(context);
            if (!passed) {
                context.addLog("Regla rota en: " + rule.getRuleName());
                return new RuleResult(false, context.evaluationDetails().toString());
            }
        }
        context.addLog("Todas las reglas de negocio pasaron con éxito.");
        return new RuleResult(true, context.evaluationDetails().toString());
    }

    public record RuleResult(boolean isApproved, String details) {}
}
