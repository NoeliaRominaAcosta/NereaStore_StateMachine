package com.comercio.application.activities;

import com.comercio.domain.model.Order;
import com.comercio.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class LogisticsActivity {

    private final OrderRepository orderRepository;

    public LogisticsActivity(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void execute(UUID orderId) {
        System.out.println("LOG ACTIVITY: Generando etiqueta de envío para la orden: " + orderId);
        
        // Simulación: Generar un tracking number ficticio
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setTrackingNumber(trackingNumber);
            orderRepository.save(order);
            System.out.println("LOG ACTIVITY: Orden embalada y tracking asignado: " + trackingNumber);
        });
    }
}
