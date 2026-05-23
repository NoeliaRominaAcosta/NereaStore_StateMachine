package com.comercio.application.service;

import com.comercio.application.dto.PurchaseEventPayload;
import com.comercio.domain.exceptions.BusinessRuleValidationException;
import com.comercio.domain.model.Order;
import com.comercio.domain.model.OrderEvent;
import com.comercio.domain.model.OrderState;
import com.comercio.domain.repository.OrderRepository;
import com.comercio.infrastructure.statemachine.OrderStateMachineInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final StateMachineFactory<OrderState, OrderEvent> stateMachineFactory;
    private final OrderStateMachineInterceptor orderStateMachineInterceptor;

    public OrderApplicationService(OrderRepository orderRepository, 
                                   StateMachineFactory<OrderState, OrderEvent> stateMachineFactory,
                                   OrderStateMachineInterceptor orderStateMachineInterceptor) {
        this.orderRepository = orderRepository;
        this.stateMachineFactory = stateMachineFactory;
        this.orderStateMachineInterceptor = orderStateMachineInterceptor;
    }

    @Transactional
    public Order initializeOrder(PurchaseEventPayload payload) {
        Order order = Order.builder()
                .id(payload.orderId())
                .customerId(payload.customerId())
                .totalAmount(payload.totalAmount())
                .currentState(OrderState.CREADA)
                .build();

        Order savedOrder = orderRepository.save(order);

        sendEvent(savedOrder.getId(), OrderEvent.INICIAR_CHECKOUT, payload);

        return orderRepository.findById(savedOrder.getId())
                .orElseThrow(() -> new RuntimeException("Error al recuperar la orden guardada"));
    }

    public void processPayment(UUID orderId) {
        sendEvent(orderId, OrderEvent.CONFIRMAR_PAGO, null);
    }

    public void packOrder(UUID orderId) {
        sendEvent(orderId, OrderEvent.EMBALAR_PEDIDO, null);
    }

    public void dispatchOrder(UUID orderId) {
        sendEvent(orderId, OrderEvent.DESPACHAR_A_CORREO, null);
    }

    public void deliverOrder(UUID orderId) {
        sendEvent(orderId, OrderEvent.ENTREGAR, null);
    }

    public void cancelOrder(UUID orderId) {
        sendEvent(orderId, OrderEvent.CANCELAR, null);
    }

    private void sendEvent(UUID orderId, OrderEvent event, PurchaseEventPayload payload) {
        StateMachine<OrderState, OrderEvent> sm = build(orderId);

        MessageBuilder<OrderEvent> builder = MessageBuilder.withPayload(event)
                .setHeader("ORDER_ID_HEADER", orderId);

        if (payload != null) {
            builder.setHeader("PURCHASE_PAYLOAD", payload);
        }

        Message<OrderEvent> message = builder.build();

        sm.sendEvent(Mono.just(message)).blockLast();

        Object errorObj = sm.getExtendedState().getVariables().get("TX_ERROR");
        if (errorObj instanceof BusinessRuleValidationException error) {
            sm.stopReactively().block();
            throw error;
        }

        sm.stopReactively().block();
    }

    private StateMachine<OrderState, OrderEvent> build(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Orden no encontrada: " + orderId));

        StateMachine<OrderState, OrderEvent> sm = stateMachineFactory.getStateMachine(orderId);

        sm.stopReactively().block();

        sm.getStateMachineAccessor()
                .doWithAllRegions(accessor -> {
                    accessor.addStateMachineInterceptor(orderStateMachineInterceptor);
                    accessor.resetStateMachineReactively(new DefaultStateMachineContext<>(
                            order.getCurrentState(), null, null, sm.getExtendedState())).block();
                });

        sm.startReactively().block();
        return sm;
    }
}
