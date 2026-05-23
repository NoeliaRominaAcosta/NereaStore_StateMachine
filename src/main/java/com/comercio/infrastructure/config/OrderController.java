package com.comercio.infrastructure.config;

import com.comercio.application.dto.PurchaseEventPayload;
import com.comercio.application.service.OrderApplicationService;
import com.comercio.domain.model.Order;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @PostMapping
    public Order createOrder(@Valid @RequestBody PurchaseEventPayload payload) {
        return orderApplicationService.initializeOrder(payload);
    }

    @PostMapping("/{id}/payment")
    public void confirmPayment(@PathVariable UUID id) {
        orderApplicationService.processPayment(id);
    }

    @PostMapping("/{id}/pack")
    public void packOrder(@PathVariable UUID id) {
        orderApplicationService.packOrder(id);
    }

    @PostMapping("/{id}/dispatch")
    public void dispatchOrder(@PathVariable UUID id) {
        orderApplicationService.dispatchOrder(id);
    }

    @PostMapping("/{id}/deliver")
    public void deliverOrder(@PathVariable UUID id) {
        orderApplicationService.deliverOrder(id);
    }

    @PostMapping("/{id}/cancel")
    public void cancelOrder(@PathVariable UUID id) {
        orderApplicationService.cancelOrder(id);
    }
}
