package com.comercio.infrastructure.statemachine;

import com.comercio.domain.exceptions.BusinessRuleValidationException;
import com.comercio.domain.model.OrderState;
import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.repository.OrderRepository;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class OrderStateMachineInterceptor extends StateMachineInterceptorAdapter<OrderState, OrderEvent> {

    private final OrderRepository orderRepository;

    public OrderStateMachineInterceptor(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void preStateChange(State<OrderState, OrderEvent> state, 
                               Message<OrderEvent> message, 
                               Transition<OrderState, OrderEvent> transition, 
                               StateMachine<OrderState, OrderEvent> stateMachine, 
                               StateMachine<OrderState, OrderEvent> rootStateMachine) {
        
        Optional.ofNullable(message)
            .flatMap(msg -> Optional.ofNullable(msg.getHeaders().get("ORDER_ID_HEADER", UUID.class)))
            .ifPresent(orderId -> {
                OrderState nextState = state.getId();
                System.out.println("LOG INTERCEPTOR: Guardando en DB para la orden " + orderId + " el estado: " + nextState);
                
                orderRepository.findById(orderId).ifPresent(order -> {
                    order.setCurrentState(nextState);
                    orderRepository.save(order);
                });
            });
    }

    @Override
    public Exception stateMachineError(StateMachine<OrderState, OrderEvent> stateMachine, 
                                      Exception exception) {
        
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause instanceof BusinessRuleValidationException bizException) {
            UUID orderId = bizException.getOrderId();
            System.err.println("CRITICAL INTERCEPTOR: Se rompió una regla de negocio para la orden " + orderId + ". Motivo: " + bizException.getMessage());
            
            stateMachine.getExtendedState().getVariables().put("TX_ERROR", bizException);
        }
        
        return exception;
    }
}
