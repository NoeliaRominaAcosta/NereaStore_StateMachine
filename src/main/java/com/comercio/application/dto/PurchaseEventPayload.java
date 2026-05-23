package com.comercio.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseEventPayload(
    @NotNull UUID orderId,
    @NotNull Long customerId,
    @NotNull @Positive BigDecimal totalAmount,
    @NotEmpty List<@Valid OrderItemDto> items,
    @NotNull @Valid ShippingAddressDto shippingAddress
) {}
