package com.comercio.application.actions;

import com.comercio.application.activities.LogisticsActivity;
import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.model.OrderState;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogisticsAction implements Action<OrderState, OrderEvent> {

    private final LogisticsActivity logisticsActivity;

    public LogisticsAction(LogisticsActivity logisticsActivity) {
        this.logisticsActivity = logisticsActivity;
    }

    @Override
    public void execute(StateContext<OrderState, OrderEvent> context) {
        UUID orderId = context.getMessage().getHeaders().get("ORDER_ID_HEADER", UUID.class);

        if (orderId != null) {
            logisticsActivity.execute(orderId);
        }
    }
}
