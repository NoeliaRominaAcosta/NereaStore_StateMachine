package com.comercio.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItemDto(
    @NotBlank String productId, 
    @NotNull @Min(1) Integer quantity
) {}
