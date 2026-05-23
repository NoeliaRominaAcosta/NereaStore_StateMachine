package com.comercio.application.actions;

import com.comercio.application.activities.ReserveStockActivity;
import com.comercio.application.dto.PurchaseEventPayload;
import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.model.OrderState;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

@Component
public class ReserveStockAction implements Action<OrderState, OrderEvent> {

    private final ReserveStockActivity reserveStockActivity;

    public ReserveStockAction(ReserveStockActivity reserveStockActivity) {
        this.reserveStockActivity = reserveStockActivity;
    }

    @Override
    public void execute(StateContext<OrderState, OrderEvent> context) {
        PurchaseEventPayload payload = context.getMessage()
                .getHeaders()
                .get("PURCHASE_PAYLOAD", PurchaseEventPayload.class);

        if (payload != null) {
            this.reserveStockActivity.execute(payload);
        } else {
            throw new IllegalStateException("Payload faltante en la transición de la State Machine");
        }
    }
}
