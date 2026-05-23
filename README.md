# Order Management State Machine

Este proyecto es una implementación profesional de una Máquina de Estados (State Machine) utilizando **Spring State Machine** para gestionar el ciclo de vida de una orden de compra. Utiliza una arquitectura desacoplada donde la lógica de negocio reside en **Activities** y las validaciones en un **Rule Engine** inyectado por Spring.

## 🚀 Tecnologías Utilizadas
- **Java 23** 
- **Spring Boot 3.3.0**
- **Spring State Machine 4.0.0**
- **Spring Data JPA** (MySQL)
- **Docker** & **Docker Compose**
- **Jakarta Validation**

---

## 🛠️ Configuración e Instalación

### 1. Requisitos Previos
- Docker y Docker Compose instalados.
- JDK 21 instalado.
- Maven instalado (o usar un IDE que lo integre).

### 2. Levantar la Base de Datos (MySQL)
El proyecto incluye un `docker-compose.yml` que configura una base de datos MySQL 8.0.

```bash
# Desde la raíz del proyecto
docker-compose up -d
```

Esto creará:
- **Base de datos:** `state_machine_db`
- **Usuario:** `user_sm`
- **Contraseña:** `password_sm`
- **Puerto:** `3306`

### 3. Ejecutar la Aplicación
```bash
mvn clean spring-boot:run
```

---

## 🛣️ Endpoints de la API

| Método | Endpoint | Descripción |
| :--- | :--- | :--- |
| `POST` | `/api/orders` | Crea una orden e inicia el checkout (Transición a `ESPERANDO_PAGO`). |
| `POST` | `/api/orders/{id}/payment` | Confirma el pago (Transición a `PAGADA`). |
| `POST` | `/api/orders/{id}/pack` | Inicia el embalaje y genera tracking (Transición a `EMBALADA`). |
| `POST` | `/api/orders/{id}/dispatch` | Despacha la orden al correo (Transición a `EN_DISTRIBUCION`). |
| `POST` | `/api/orders/{id}/deliver` | Marca la orden como entregada (Transición FINAL `ENTREGADA`). |
| `POST` | `/api/orders/{id}/cancel` | Cancela la orden (Transición FINAL `CANCELADA`). |

---

## 🧪 Ejemplo de Flujo de Trabajo

### Paso 1: Crear una Orden (Inicia Checkout)
```bash
curl -X POST http://localhost:8080/api/orders \
-H "Content-Type: application/json" \
-d '{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": 101,
  "totalAmount": 1500.00,
  "items": [{"productId": "LAPTOP-01", "quantity": 1}],
  "shippingAddress": {"street": "Av. Siempreviva 742", "city": "Springfield", "zipCode": "62704"}
}'
```

### Paso 2: Confirmar Pago
```bash
curl -X POST http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000/payment
```

### Paso 3: Embalar (Genera Tracking Number)
```bash
curl -X POST http://localhost:8080/api/orders/550e8400-e29b-41d4-a716-446655440000/pack
```

---

## 🧩 Arquitectura
1. **Controller:** Expone los endpoints REST.
2. **Application Service:** Orquestador. Hidrata la máquina de estados desde la DB.
3. **State Machine Interceptor:** Persiste automáticamente el estado en la DB en cada transición y captura excepciones de negocio.
4. **Actions:** Adaptadores que se disparan en las transiciones para llamar a las Activities.
5. **Activities:** Contienen la lógica de negocio pura (Llamadas a Rule Engine, actualización de DB, etc).
6. **Rule Engine:** Motor de reglas inyectado por Spring (`@Order`) para validaciones dinámicas.
