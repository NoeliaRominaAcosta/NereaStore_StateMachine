package com.comercio.infrastructure.statemachine;

import com.comercio.application.actions.LogisticsAction;
import com.comercio.application.actions.ProcessPaymentAction;
import com.comercio.application.actions.ReserveStockAction;
import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.model.OrderState;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
public class StateMachineConfig extends StateMachineConfigurerAdapter<OrderState, OrderEvent> {

    private final ReserveStockAction reserveStockAction;
    private final ProcessPaymentAction processPaymentAction;
    private final LogisticsAction logisticsAction;

    public StateMachineConfig(ReserveStockAction reserveStockAction,
                              ProcessPaymentAction processPaymentAction,
                              LogisticsAction logisticsAction) {
        this.reserveStockAction = reserveStockAction;
        this.processPaymentAction = processPaymentAction;
        this.logisticsAction = logisticsAction;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderState, OrderEvent> config) throws Exception {
        config
                .withConfiguration()
                .autoStartup(false)
                .listener(new StateMachineLogListener());
    }

    @Override
    public void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states) throws Exception {
        states
                .withStates()
                .initial(OrderState.CREADA)
                .states(EnumSet.allOf(OrderState.class))
                .end(OrderState.ENTREGADA)
                .end(OrderState.CANCELADA);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(OrderState.CREADA).target(OrderState.ESPERANDO_PAGO)
                .event(OrderEvent.INICIAR_CHECKOUT)
                .action(reserveStockAction)
                .and()
                .withExternal()
                .source(OrderState.ESPERANDO_PAGO).target(OrderState.PAGADA)
                .event(OrderEvent.CONFIRMAR_PAGO)
                .action(processPaymentAction)
                .and()
                .withExternal()
                .source(OrderState.PAGADA).target(OrderState.EMBALADA)
                .event(OrderEvent.EMBALAR_PEDIDO)
                .action(logisticsAction)
                .and()
                .withExternal()
                .source(OrderState.EMBALADA).target(OrderState.EN_DISTRIBUCION)
                .event(OrderEvent.DESPACHAR_A_CORREO)
                .and()
                .withExternal()
                .source(OrderState.EN_DISTRIBUCION).target(OrderState.ENTREGADA)
                .event(OrderEvent.ENTREGAR)
                .and()
                .withExternal()
                .source(OrderState.CREADA).target(OrderState.CANCELADA)
                .event(OrderEvent.CANCELAR)
                .and()
                .withExternal()
                .source(OrderState.ESPERANDO_PAGO).target(OrderState.CANCELADA)
                .event(OrderEvent.CANCELAR);
    }
}
