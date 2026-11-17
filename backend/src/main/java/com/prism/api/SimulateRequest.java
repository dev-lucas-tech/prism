package com.prism.api;

import com.prism.fx.Corridor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SimulateRequest(
        @NotNull Corridor corridor,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String merchant
) {
}
