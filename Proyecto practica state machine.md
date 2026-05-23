A continuación, estructuramos el diseño del payload, el flujo de responsabilidades y el orden cronológico de ejecución desde que ingresa la compra.

## **1\. El Payload: Estructura del Evento de Compra**

Para el ingreso de la compra, necesitamos un objeto inmutable que transporte toda la información necesaria. En Java 21, los record son perfectos para esto.

Java  
// El payload genérico o específico que viaja dentro del evento de la State Machine  
public record PurchaseEventPayload(  
    UUID orderId,  
    Long customerId,  
    BigDecimal totalAmount,  
    List\<OrderItemDto\> items,  
    ShippingAddressDto shippingAddress  
) {}

public record OrderItemDto(String productId, Integer quantity) {}  
public record ShippingAddressDto(String street, String city, String zipCode) {}

## **2\. Componentes y Capas (Buenas Prácticas)**

Para mantener el desacoplamiento, la cadena de llamados sigue este orden de responsabilidades:

1. **OrderController (Interface / Adaptador de Entrada):** Recibe la petición HTTP, valida el DTO de entrada y delega inmediatamente al servicio de aplicación. **No conoce la State Machine.**  
2. **OrderApplicationService (Orquestador / Caso de Uso):** Es el director de orquesta. Levanta la orden de la base de datos (o la crea), recupera la instancia de la StateMachine para esa orden a través del StateMachineFactory, y le envía el OrderEvent adjuntando el *payload* en los headers.  
3. **OrderStateMachineConfig (Filtro / Transición):** Spring SM valida si el evento es permitido en el estado actual. Si pasa los Guards, ejecuta la Action asociada a la transición.  
4. **OrderAction (Adaptador de la SM):** Es un componente de infraestructura de la máquina de estados. Su única tarea es extraer el *payload* de los headers del mensaje y llamar a la Activity correspondiente.  
5. **OrderActivity (Lógica de Negocio / Dominio):** Aquí vive la verdad del negocio. Llama al *Rule Engine*, impacta los cambios en el *Repository* (JPA/Hibernate) y se comunica con servicios externos (pagos, correos).

## **3\. Flujo Cronológico: Paso a Paso de los Procesos**

Vamos a desglosar el orden exacto de ejecución, los componentes involucrados y cuándo ocurren los save o update en la base de datos para cada fase crítica.

### **Proceso A: Ingreso y Creación de la Compra (CREADA ➔ ESPERANDO\_PAGO)**

Este es el flujo inicial cuando el cliente hace clic en "Proceder al Pago".

\[Cliente\] ──(HTTP POST)──► \[Controller\] ──► \[AppService\] ──► \[State Machine\] ──► \[Action\] ──► \[Activity\] ──► \[DB / Rule Engine\]

1. **Ingreso:** El cliente envía el carrito de compras al OrderController.  
2. **Validación Inicial:** El controlador recibe el CreateOrderRequest, valida formatos y llama a orderApplicationService.initializeOrder(payload).  
3. **Primer Registro (Pre-Estado):** El OrderApplicationService genera un UUID para la orden, crea la entidad Order en estado **CREADA** y realiza el **primer repository.save(order)** para garantizar que la orden exista físicamente en la base de datos antes de que la máquina empiece a transicionar.  
4. **Disparo a la State Machine:** El servicio solicita una máquina de estados para ese UUID, la inicializa en el estado CREADA (leyendo de la DB) y le envía el evento INICIAR\_CHECKOUT, adjuntando el PurchaseEventPayload en los headers del mensaje.  
5. **Intercepción y Guard:** La State Machine evalúa la transición. Ejecuta el StockGuard. Este guard puede llamar a un servicio de inventario para chequear disponibilidad. Si da OK, se autoriza la transición.  
6. **Ejecución de la Action:** Se dispara la ReserveStockAction. Esta extrae el PurchaseEventPayload de los headers y delega el control llamando a reserveStockActivity.execute(payload).  
7. **Ejecución de la Activity & Reglas:**  
   * La Activity invoca al **Rule Engine** (Motor de Reglas) para verificar políticas de riesgo del cliente o aplicar descuentos dinámicos.  
   * La Activity ejecuta la lógica de reserva de stock.  
   * La Activity hace un **update en la base de datos** reflejando el stock reservado temporalmente.  
8. **Persistencia del Estado:** Al finalizar la acción con éxito, el StateMachineInterceptor intercepta el cambio de estado de la máquina a **ESPERANDO\_PAGO** y hace el **repository.updateState(orderId, ESPERANDO\_PAGO)** en la tabla de órdenes, además de insertar el registro en el historial de auditoría.

### **Proceso B: Confirmación del Pago (ESPERANDO\_PAGO ➔ PAGADA)**

Este proceso se dispara de forma asincrónica cuando la pasarela de pagos (ej. Mercado Pago o Webhook de tarjetas) avisa que el cobro fue exitoso.

1. **Ingreso:** El PaymentWebhookController recibe la notificación del pago con el orderId y el paymentStatus. Delega a orderApplicationService.confirmPayment(orderId, paymentDetails).  
2. **Recuperación del Estado:** El servicio levanta la orden de la DB (que está en ESPERANDO\_PAGO) e hidrata/arranca la StateMachine en ese estado exacto.  
3. **Disparo del Evento:** El servicio envía el evento CONFIRMAR\_PAGO con los datos de la transacción en el header.  
4. **Action a Activity:** La máquina valida la transición hacia PAGADA y dispara la ProcessPaymentAction, que le pasa el control a la ProcessPaymentActivity.  
5. **Lógica de Negocio (Activity):**  
   * La activity valida los datos del pago contra la pasarela.  
   * Registra el cobro en una tabla de payments.  
6. **Persistencia del Estado:** El interceptor de la State Machine actualiza la orden en la base de datos al estado **PAGADA**.

### **Proceso C: Preparación y Embalaje (PAGADA ➔ EMBALADA)**

Este flujo suele ser disparado por un operador del depósito a través de una interfaz interna o sistema de picking.

1. **Ingreso:** El operario escanea el pedido y presiona "Listo para Despachar". El WarehouseController recibe la petición y llama al servicio.  
2. **Disparo del Evento:** Se envía el evento EMBALAR\_PEDIDO a la máquina de estados.  
3. **Action a Activity:** Se ejecuta la LogisticsAction que invoca a la GenerateShippingActivity.  
4. **Lógica de Negocio (Activity):** La activity interactúa con el **Rule Engine de Logística** para determinar qué correo es el más óptimo según el código postal, le pega a la API del correo de forma simulada, obtiene el tracking number y genera la etiqueta.  
5. **Persistencia y Update:** La activity hace un **update en la base de datos** para guardar el tracking\_number en la orden. Inmediatamente después, el interceptor de la máquina guarda el nuevo estado de la orden como **EMBALADA**.

### **Proceso D: Despacho y Distribución (EMBALADA ➔ EN\_DISTRIBUCION)**

Ocurre cuando el camión del correo retira los paquetes del comercio.

1. **Ingreso:** Sistema batch o webhook del correo notificando la admisión del paquete.  
2. **Disparo del Evento:** Se envía el evento DESPACHAR\_A\_CORREO.  
3. **Action a Activity:** La DispatchAction llama a DispatchActivity.  
4. **Lógica de Negocio (Activity):** Dispara la NotifyCustomerActivity (envía un mail ficticio al cliente con su tracking de seguimiento).  
5. **Persistencia del Estado:** El interceptor actualiza el estado de la orden en la DB a **EN\_DISTRIBUCION**.

## **4\. Ejemplo de Código: Conectando Action, Header y Activity**

Para ver cómo interactúan estos componentes en Java con Spring SM, mirá este boceto de la Action y cómo extrae el payload para la Activity:

Java  
@Component  
public class ReserveStockAction implements Action\<OrderState, OrderEvent\> {

    private final ReserveStockActivity reserveStockActivity;

    public ReserveStockAction(ReserveStockActivity reserveStockActivity) {  
        this.reserveStockActivity \= reserveStockActivity;  
    }

    @Override  
    public void execute(StateContext\<OrderState, OrderEvent\> context) {  
        // 1\. Extraer el payload de los headers del mensaje de la State Machine  
        PurchaseEventPayload payload \= context.getMessage()  
            .getHeaders()  
            .get("PURCHASE\_PAYLOAD", PurchaseEventPayload.class);

        if (payload \!= null) {  
            // 2\. Delegar la lógica de negocio pura a la Activity  
            this.reserveStockActivity.execute(payload);  
        } else {  
            throw new IllegalStateException("Payload faltante en la transicion de la State Machine");  
        }  
    }  
}

Este diseño te asegura que si el día de mañana decidís migrar de Spring State Machine a otra tecnología (como Camunda, Temporal o un motor propio), **tus Activities y tu modelo de base de datos quedan 100% intactos**, ya que toda tu lógica de negocio está resguardada en servicios puros de Java.

Vamos a diseñar un motor de reglas que evalúe si una orden es apta para avanzar basándose en el inventario y políticas de negocio.

## **1\. La Arquitectura del Rule Engine**

El motor se compondrá de tres partes:

1. **La Interfaz de la Regla (`BusinessRule`):** Define qué es una regla.  
2. **Las Implementaciones:** Cada regla de negocio mapeada a una clase inmutable.  
3. **El Motor Orquestador (`OrderRuleEngine`):** Recibe todas las reglas registradas en el contenedor de Spring y las ejecuta en cadena.

                 ┌──► \[StockAvailableRule\] (Valida inventario)  
                  │  
\[OrderRuleEngine\] ┼──► \[CustomerRiskRule\]   (Valida fraude/límites)  
                  │  
                  └──► \[ShippingZoneRule\]   (Valida cobertura del correo)

## **2\. El Código Paso a Paso**

### **A. El Contexto de la Regla (Payload)**

Para que las reglas evalúen el negocio, necesitan datos. Usamos un `record` de Java 21 para pasar el contexto necesario.

Java  
package com.comercio.domain.rules;

import com.comercio.domain.dto.PurchaseEventPayload;

// El contexto viaja por las reglas. Podés agregar campos de salida si una regla altera datos.  
public record RuleContext(  
    PurchaseEventPayload payload,  
    StringBuilder evaluationDetails  
) {  
    public void addLog(String message) {  
        evaluationDetails.append(message).append(" | ");  
    }  
}

### **B. La Interfaz Base de las Reglas**

Cada regla sabrá si aplica a un escenario específico y si el contexto es válido.

Java  
package com.comercio.domain.rules;

public interface BusinessRule {  
    boolean evaluate(RuleContext context);  
    String getRuleName();  
    int getOrder(); // Para definir cuál se ejecuta primero si el orden importa  
}

### **C. Implementación de Reglas Concretas (Componentes Spring)**

Acá inyectás los repositorios o clientes HTTP que necesites para validar la información real.

Java  
package com.comercio.infrastructure.rules;

import com.comercio.domain.rules.BusinessRule;  
import com.comercio.domain.rules.RuleContext;  
import org.springframework.stereotype.Component;

@Component  
public class StockAvailableRule implements BusinessRule {

    // private final InventoryRepository inventoryRepository; // Inyectás tu persistencia aquí

    @Override  
    public boolean evaluate(RuleContext context) {  
        context.addLog("Evaluando disponibilidad de Stock");  
          
        // Simulación: Recorremos los ítems del payload  
        boolean allItemsAvailable \= context.payload().items().stream()  
                .allMatch(item \-\> item.quantity() \< 100); // Lógica de negocio simulada

        if (\!allItemsAvailable) {  
            context.addLog("Fallo: Uno o más productos no tienen stock suficiente.");  
            return false;  
        }  
        return true;  
    }

    @Override  
    public String getRuleName() { return "STOCK\_AVAILABILITY\_RULE"; }

    @Override  
    public int getOrder() { return 1; } // Se ejecuta primero  
}

Java  
package com.comercio.infrastructure.rules;

import com.comercio.domain.rules.BusinessRule;  
import com.comercio.domain.rules.RuleContext;  
import org.springframework.stereotype.Component;  
import java.math.BigDecimal;

@Component  
public class CustomerRiskRule implements BusinessRule {

    @Override  
    public boolean evaluate(RuleContext context) {  
        context.addLog("Evaluando riesgo crediticio del cliente");  
          
        // Ejemplo: Si el monto es ridículamente alto, requiere otra verificación (ficticia)  
        if (context.payload().totalAmount().compareTo(new BigDecimal("1000000")) \> 0\) {  
            context.addLog("Fallo: Monto excede el límite de fraude automatizado.");  
            return false;  
        }  
        return true;  
    }

    @Override  
    public String getRuleName() { return "CUSTOMER\_RISK\_RULE"; }

    @Override  
    public int getOrder() { return 2; }  
}

### **D. El Orquestador: `OrderRuleEngine`**

La magia de Spring: Al usar `List<BusinessRule>`, Spring busca **todas** las clases que implementen esa interfaz, las instancia y las inyecta ordenadas de forma automática gracias al `.stream().sorted()`.

Java  
package com.comercio.domain.rules;

import org.springframework.stereotype.Service;  
import java.util.Comparator;  
import java.util.List;

@Service  
public class OrderRuleEngine {

    private final List\<BusinessRule\> rules;

    // Spring inyecta automáticamente todos los @Component que implementen BusinessRule  
    public OrderRuleEngine(List\<BusinessRule\> rules) {  
        this.rules \= rules.stream()  
                .sorted(Comparator.comparingInt(BusinessRule::getOrder))  
                .toList();  
    }

    public RuleResult processRules(RuleContext context) {  
        for (BusinessRule rule : rules) {  
            boolean passed \= rule.evaluate(context);  
            if (\!passed) {  
                context.addLog("Regla rota en: " \+ rule.getRuleName());  
                return new RuleResult(false, context.evaluationDetails().toString());  
            }  
        }  
        context.addLog("Todas las reglas de negocio pasaron con éxito.");  
        return new RuleResult(true, context.evaluationDetails().toString());  
    }

    public record RuleResult(boolean isApproved, String details) {}  
}

## **3\. ¿Cómo interactúa esto con la Activity?**

Tu `Activity` (invocada por la `Action` de la State Machine) va a recibir el payload, instanciará el contexto del motor de reglas, y tomará decisiones basadas en el resultado.

Java  
package com.comercio.application.activities;

import com.comercio.domain.dto.PurchaseEventPayload;  
import com.comercio.domain.rules.OrderRuleEngine;  
import com.comercio.domain.rules.RuleContext;  
import org.springframework.stereotype.Component;

@Component  
public class ReserveStockActivity {

    private final OrderRuleEngine ruleEngine;  
    // private final OrderRepository orderRepository;

    public ReserveStockActivity(OrderRuleEngine ruleEngine) {  
        this.ruleEngine \= ruleEngine;  
    }

    public void execute(PurchaseEventPayload payload) {  
        // 1\. Inicializar el contexto para el Rule Engine  
        RuleContext context \= new RuleContext(payload, new StringBuilder("Iniciando procesamiento: "));

        // 2\. Ejecutar el motor de reglas  
        OrderRuleEngine.RuleResult result \= ruleEngine.processRules(context);

        if (\!result.isApproved()) {  
            // Acá manejás la frustración del negocio: Lanzás una excepción específica de dominio  
            // que luego tu State Machine o tu AppService atrapará para desviar el flujo a CANCELADA.  
            throw new BusinessRuleValidationException("La orden no cumple las condiciones: " \+ result.details());  
        }

        // 3\. Si pasó las reglas, se efectúa la lógica y el impacto en DB (Save/Update)  
        System.out.println("LOG: Reglas aprobadas. Guardando reserva de stock en DB...");  
        // orderRepository.saveAndFlush(...);  
    }  
}

El `StateMachineInterceptor` es la pieza fundamental para transformar una máquina de estados de juguete (que vive en la memoria RAM) en un sistema transaccional de nivel producción.

Tiene dos responsabilidades cruciales:

1. **Persistencia Dinámica:** Intercepta el cambio de estado *antes* de que ocurra y lo impacta en la base de datos dentro de la misma transacción.  
2. **Manejo de Excepciones:** Si una de tus `Activities` (o el *Rule Engine*) lanza una excepción de negocio (como quedarnos sin stock), el interceptor la captura, frena la transición original y nos permite desviar el flujo de forma segura hacia el estado de falla (`CANCELADA`).

Acá tenés la estructura limpia y profesional para implementarlo en tu proyecto.

## **1\. La Excepción de Dominio**

Primero, definimos una excepción en nuestra capa de dominio que contenga el contexto de la orden. Esto nos permitirá saber qué pedido falló cuando la máquina intente procesar la lógica.

Java  
package com.comercio.domain.exceptions;

import java.util.UUID;

public class BusinessRuleValidationException extends RuntimeException {  
    private final UUID orderId;

    public BusinessRuleValidationException(UUID orderId, String message) {  
        super(message);  
        this.orderId \= orderId;  
    }

    public UUID getOrderId() {  
        return orderId;  
    }  
}

## **2\. Implementación del `OrderStateMachineInterceptor`**

Heredamos de `StateMachineInterceptorAdapter`. El truco acá es usar **`preStateChange`** para guardar el estado en la base de datos y **`stateMachineError`** junto con el contexto del mensaje para capturar la anomalía.

Java  
package com.comercio.infrastructure.statemachine;

import com.comercio.domain.exceptions.BusinessRuleValidationException;  
import com.comercio.domain.model.OrderState;  
import com.comercio.domain.model.OrderEvent;  
import com.comercio.domain.repository.OrderRepository; // Tu repositorio JPA  
import org.springframework.messaging.Message;  
import org.springframework.statemachine.StateMachine;  
import org.springframework.statemachine.state.State;  
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;  
import org.springframework.statemachine.support.StateMachineMessageHeaders;  
import org.springframework.stereotype.Component;  
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;  
import java.util.UUID;

@Component  
public class OrderStateMachineInterceptor extends StateMachineInterceptorAdapter\<OrderState, OrderEvent\> {

    private final OrderRepository orderRepository;

    public OrderStateMachineInterceptor(OrderRepository orderRepository) {  
        this.orderRepository \= orderRepository;  
    }

    /\*\*  
     \* Se ejecuta ANTES de que el estado cambie en la máquina de estados.  
     \* Ideal para persistir el nuevo estado en la base de datos.  
     \*/  
    @Override  
    @Transactional  
    public void preStateChange(State\<OrderState, OrderEvent\> state,   
                               Message\<OrderEvent\> message,   
                               org.springframework.statemachine.transition.Transition\<OrderState, OrderEvent\> transition,   
                               StateMachine\<OrderState, OrderEvent\> stateMachine,   
                               StateMachine\<OrderState, OrderEvent\> rootStateMachine) {  
          
        // Recuperamos el ID de la orden desde los headers que enviamos en el controlador/servicio  
        Optional.ofNullable(message)  
            .flatMap(msg \-\> Optional.ofNullable(msg.getHeaders().get("ORDER\_ID\_HEADER", UUID.class)))  
            .ifPresent(orderId \-\> {  
                OrderState nextState \= state.getId();  
                System.out.println("LOG INTERCEPTOR: Guardando en DB para la orden " \+ orderId \+ " el estado: " \+ nextState);  
                  
                // Buscamos la orden e impactamos el estado de forma transaccional  
                orderRepository.findById(orderId).ifPresent(order \-\> {  
                    order.setCurrentState(nextState);  
                    orderRepository.save(order); // Provoca el UPDATE en la base de datos  
                });  
            });  
    }

    /\*\*  
     \* Se ejecuta si ocurre una excepción dentro de una Action o Activity durante la transición.  
     \*/  
    @Override  
    public StateMachine\<OrderState, OrderEvent\> stateMachineError(StateMachine\<OrderState, OrderEvent\> stateMachine,   
                                                                  Exception exception) {  
          
        // Buscamos si la causa raíz es nuestra excepción de negocio  
        Throwable rootCause \= exception;  
        while (rootCause.getCause() \!= null) {  
            rootCause \= rootCause.getCause();  
        }

        if (rootCause instanceof BusinessRuleValidationException bizException) {  
            UUID orderId \= bizException.getOrderId();  
            System.err.println("CRITICAL INTERCEPTOR: Se rompió una regla de negocio para la orden " \+ orderId \+ ". Motivo: " \+ bizException.getMessage());  
              
            // Guardamos la excepción en el contexto extendido de la máquina para que el Application Service se entere  
            stateMachine.getExtendedState().getVariables().put("TX\_ERROR", bizException);  
              
            // Forzamos asincrónicamente el desvío del flujo enviando el evento de cancelación  
            // Usamos un hilo separado o delegamos para evitar loops muertos en la misma transición  
            UUID finalOrderId \= orderId;  
            org.springframework.cache.support.SimpleValueWrapper wrapper;   
              
            // Tip Pro: Podés lanzar un evento inmediato de falla si tu arquitectura lo requiere  
            // stateMachine.sendEvent(MessageBuilder.withPayload(OrderEvent.EXPIRAR\_TIEMPO\_PAGO)...);  
        }  
          
        return super.stateMachineError(stateMachine, exception);  
    }  
}

## **3\. Registro del Interceptor en la Configuración**

Para que Spring SM sepa que este interceptor existe, tenés que registrarlo dentro de tu clase principal de configuración `@Configuration` de la máquina de estados.

Java  
package com.comercio.infrastructure.statemachine;

import com.comercio.domain.model.OrderState;  
import com.comercio.domain.model.OrderEvent;  
import org.springframework.context.annotation.Configuration;  
import org.springframework.statemachine.config.EnableStateMachineFactory;  
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;  
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;

@Configuration  
@EnableStateMachineFactory  
public class StateMachineConfig extends StateMachineConfigurerAdapter\<OrderState, OrderEvent\> {

    private final OrderStateMachineInterceptor orderStateMachineInterceptor;

    public StateMachineConfig(OrderStateMachineInterceptor orderStateMachineInterceptor) {  
        this.orderStateMachineInterceptor \= orderStateMachineInterceptor;  
    }

    @Override  
    public void configure(StateMachineConfigurationConfigurer\<OrderState, OrderEvent\> config) throws Exception {  
        config  
            .withConfiguration()  
            .autoStartup(false) // Lo manejamos manualmente desde el Service  
            .listener(new StateMachineLogListener()) // Opcional: para meter logs limpios de transiciones  
            .and()  
            .withRegisteredTransitions()  
            .and()  
            .withConfiguration()  
            .interceptor(orderStateMachineInterceptor); // \<-- ACÁ REGISTRAMOS TU INTERCEPTOR  
    }  
      
    // Acá irían tus configuraciones de withStates y withTransitions...  
}

## **4\. El Orquestador: Cómo reacciona el Application Service**

Cuando ocurre un error, el interceptor guarda la excepción en el `ExtendedState`. Tu servicio puede revisar esa variable después de enviar el evento para saber si todo salió bien o si tiene que arrojarle una respuesta específica al controlador HTTP.

Java  
package com.comercio.application.service;

import com.comercio.domain.exceptions.BusinessRuleValidationException;  
import com.comercio.domain.model.OrderEvent;  
import com.comercio.domain.model.OrderState;  
import org.springframework.messaging.Message;  
import org.springframework.messaging.support.MessageBuilder;  
import org.springframework.statemachine.StateMachine;  
import org.springframework.statemachine.config.StateMachineFactory;  
import org.springframework.stereotype.Service;  
import java.util.UUID;

@Service  
public class OrderApplicationService {

    private final StateMachineFactory\<OrderState, OrderEvent\> stateMachineFactory;

    public OrderApplicationService(StateMachineFactory\<OrderState, OrderEvent\> stateMachineFactory) {  
        this.stateMachineFactory \= stateMachineFactory;  
    }

    public void processOrderCheckout(UUID orderId, PurchaseEventPayload payload) {  
        // 1\. Rehidratamos la máquina para esta orden específica  
        StateMachine\<OrderState, OrderEvent\> sm \= stateMachineFactory.getStateMachine(orderId);  
          
        // 2\. Construimos el mensaje con los headers necesarios para el interceptor y las actions  
        Message\<OrderEvent\> message \= MessageBuilder.withPayload(OrderEvent.INICIAR\_CHECKOUT)  
                .setHeader("ORDER\_ID\_HEADER", orderId)  
                .setHeader("PURCHASE\_PAYLOAD", payload)  
                .build();

        // 3\. Arrancamos la máquina y enviamos el evento  
        sm.startReactively().subscribe();  
        sm.sendEvent(message);

        // 4\. Verificamos si el interceptor atrapó algún error de negocio durante la ejecución sincrónica  
        BusinessRuleValidationException error \= sm.getExtendedState()  
                .getVariables()  
                .get("TX\_ERROR", BusinessRuleValidationException.class);

        if (error \!= null) {  
            // El flujo falló. La base de datos no cambió de estado (o cambió a cancelada)   
            // y le propagamos el error al Controller para que devuelva un HTTP 400 o 422\.  
            throw error;  
        }  
          
        sm.stopReactively().subscribe();  
    }  
}  
