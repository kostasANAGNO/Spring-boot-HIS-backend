package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.MedicationRoute;
import com.kostasanagno.his.entity.MedicationSchedule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A medication order for an ADMITTED hospitalization. {@code intervalHours} is required for SCHEDULED (every N hours)
 * and AS_NEEDED (minimum spacing) and must be empty for ONCE. {@code startTime} defaults to now and cannot precede the
 * admission; {@code endTime} (optional) must be after the start.
 */
public record MedicationOrderRequest(
        @NotNull Long hospitalizationId,
        @NotNull Long medicationId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal doseAmount,
        @NotBlank @Size(max = 20) String doseUnit,
        @NotNull MedicationRoute route,
        @NotNull MedicationSchedule scheduleType,
        @Min(1) @Max(24) Integer intervalHours,
        Instant startTime,
        Instant endTime,
        @Size(max = 1000) String instructions) {
}
