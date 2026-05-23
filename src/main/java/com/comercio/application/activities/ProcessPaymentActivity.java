package com.comercio.application.activities;

import com.comercio.application.dto.PurchaseEventPayload;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProcessPaymentActivity {

    public void execute(UUID orderId) {
        // Simulación de lógica de negocio para procesar el pago
        System.out.println("LOG ACTIVITY: Procesando pago para la orden: " + orderId);
        // Aquí se llamaría a un servicio externo de pagos (Stripe, PayPal, etc.)
        System.out.println("LOG ACTIVITY: Pago aprobado para la orden: " + orderId);
    }
}
