package com.kostasanagno.his.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Records a supply handed out for an order: the number of units and an optional note. */
public record DispenseRequest(@NotNull @Min(1) @Max(100000) Integer quantity, @Size(max = 500) String notes) {
}
