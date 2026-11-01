package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** {@code paidAt} defaults to now and must not be in the future; {@code amount} cannot exceed what is still due. */
public record PaymentRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull PaymentMethod method,
        Instant paidAt,
        @Size(max = 100) String reference) {
}
