package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AdministrationOutcome;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Records what happened at the bedside. {@code administeredAt} defaults to now (not in the future);
 * {@code doseAmount} applies to GIVEN only, defaults to the ordered dose and can never exceed it.
 */
public record AdministrationRequest(
        @NotNull AdministrationOutcome outcome,
        Instant administeredAt,
        @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal doseAmount,
        @Size(max = 1000) String notes) {
}
