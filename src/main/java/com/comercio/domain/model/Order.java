package com.comercio.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private UUID id;

    private Long customerId;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderState currentState;

    private String trackingNumber;
}
