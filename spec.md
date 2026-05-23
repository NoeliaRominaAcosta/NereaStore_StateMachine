Technical Specification: Order Management State Machine

1. Configuración Inicial y Tecnologías

- Lenguaje: Java 21 (Uso intensivo de record y sealed interfaces si aplica).
- Framework: Spring Boot 3.x.
- Gestor de Dependencias: Maven.
- Base de Datos: MySQL 8.0 (Dockerizado).
- Dependencias Principales:
    - spring-boot-starter-web (API REST)
    - spring-boot-starter-data-jpa (Persistencia)
    - spring-statemachine-starter (Motor de Estados)
    - mysql-connector-j (Driver BD)
    - lombok (Opcional, aunque usaremos records)
    - spring-boot-starter-validation (Validación de DTOs)

  ---

2. Infraestructura (Docker)
   Crearemos un archivo docker-compose.yml en la raíz para la base de datos.

   1 services:
   2 mysql-db:
   3 image: mysql:8.0
   4 container_name: order-db
   5 environment:
   6 MYSQL_ROOT_PASSWORD: root
   7 MYSQL_DATABASE: state_machine_db
   8 MYSQL_USER: user_sm
   9 MYSQL_PASSWORD: password_sm
   10 ports:
   11 - "3306:3306"

  ---

3. Estructura del Proyecto (Paquetes)

   1 com.nerea
   2 ├── application
   3 │ ├── activities # Lógica de negocio pura (Activities)
   4 │ ├── dto # Records para transporte de datos
   5 │ └── service # Orquestadores (Application Services)
   6 ├── domain
   7 │ ├── exceptions # Excepciones de negocio
   8 │ ├── model # Entidades JPA y Enums (State, Event)
   9 │ ├── repository # Interfaces de persistencia
   10 │ └── rules # Rule Engine (Interfaces y Motor)
   11 └── infrastructure
   12 ├── config # Configuraciones generales
   13 ├── rules # Implementaciones concretas de reglas
   14 ├── statemachine # Configuración de Spring SM, Actions, Guards, Interceptores
   15 └── persistence # Adaptadores de base de datos (si aplica)

  ---

4. Plan de Desarrollo por Etapas

Etapa 1: Cimientos y Dominio

* Tarea 1.1: Generar el proyecto en Spring Initializr (https://start.spring.io/).
* Tarea 1.2: Configurar application.yml para la conexión a MySQL.
* Tarea 1.3: Crear Enums OrderState y OrderEvent.
* Tarea 1.4: Crear la entidad Order con los campos: id (UUID), currentState, totalAmount, customerId,
  trackingNumber.
* Tarea 1.5: Crear los records del Payload (PurchaseEventPayload, etc.).

Etapa 2: Motor de Reglas (Rule Engine Spring)

* Tarea 2.1: Definir la interfaz BusinessRule.
* Tarea 2.2: Implementar el OrderRuleEngine que inyecta y ordena las reglas.
* Tarea 2.3: Crear reglas básicas: StockAvailableRule y CustomerRiskRule.

Etapa 3: Infraestructura de State Machine

* Tarea 3.1: Crear OrderStateMachineConfig definiendo estados iniciales, finales y transiciones.
* Tarea 3.2: Implementar el OrderStateMachineInterceptor para persistencia en BD y manejo de errores.
* Tarea 3.3: Crear las Actions (ej: ReserveStockAction) que extraen el payload y llaman a las
  Activities.
* Tarea 3.4: Crear Guards (ej: StockGuard) para validaciones previas a la transición.

Etapa 4: Lógica de Negocio (Activities)

* Tarea 4.1: Implementar ReserveStockActivity (invoca al Rule Engine y actualiza inventario simulado).
* Tarea 4.2: Implementar ProcessPaymentActivity (simulación de pasarela).
* Tarea 4.3: Implementar LogisticsActivity (generación de tracking simulado).

Etapa 5: Capa de Aplicación y API

* Tarea 5.1: Crear el OrderApplicationService para orquestar la carga de la orden y el disparo de
  eventos a la SM.
* Tarea 5.2: Crear OrderController con endpoints:
    - POST /api/orders (Iniciar checkout)
    - POST /api/orders/{id}/payment (Confirmar pago)
    - POST /api/orders/{id}/pack (Embalar)
* Tarea 5.3: Implementar un GlobalExceptionHandler para capturar BusinessRuleValidationException.

  ---

5. Paso a Paso de Ejecución (Setup)

1. Levantar BD: docker-compose up -d.
2. Inicializar Proyecto: Usar Spring Initializr con Java 21.
3. Configurar Conexión:

1 spring.datasource.url=jdbc:mysql://localhost:3306/state_machine_db
2 spring.datasource.username=user_sm
3 spring.datasource.password=password_sm
4 spring.jpa.hibernate.ddl-auto=update

4. Implementación Incremental: Empezar por el dominio, luego el motor de reglas, y finalmente conectar
   todo con la State Machine.