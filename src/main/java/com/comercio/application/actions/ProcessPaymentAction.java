package com.comercio.application.actions;

import com.comercio.application.activities.ProcessPaymentActivity;
import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.model.OrderState;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProcessPaymentAction implements Action<OrderState, OrderEvent> {

    private final ProcessPaymentActivity processPaymentActivity;

    public ProcessPaymentAction(ProcessPaymentActivity processPaymentActivity) {
        this.processPaymentActivity = processPaymentActivity;
    }

    @Override
    public void execute(StateContext<OrderState, OrderEvent> context) {
        UUID orderId = context.getMessage().getHeaders().get("ORDER_ID_HEADER", UUID.class);

        if (orderId != null) {
            processPaymentActivity.execute(orderId);
        }
    }
}
