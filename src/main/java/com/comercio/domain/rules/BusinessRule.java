package com.comercio.domain.rules;

public interface BusinessRule {
    boolean evaluate(RuleContext context);
    String getRuleName();
}
