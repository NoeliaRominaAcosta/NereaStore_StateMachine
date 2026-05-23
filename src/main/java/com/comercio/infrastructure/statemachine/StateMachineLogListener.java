package com.comercio.infrastructure.statemachine;

import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.model.OrderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

public class StateMachineLogListener extends StateMachineListenerAdapter<OrderState, OrderEvent> {

    private static final Logger log = LoggerFactory.getLogger(StateMachineLogListener.class);

    @Override
    public void stateChanged(State<OrderState, OrderEvent> from, State<OrderState, OrderEvent> to) {
        log.info("Transitioned from {} to {}", 
            from != null ? from.getId() : "START", 
            to != null ? to.getId() : "END");
    }
}
