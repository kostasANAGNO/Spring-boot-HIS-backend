package com.kostasanagno.his.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A price list entry. {@code unitPrice} is in EUR without VAT; {@code vatRate} is a percentage (0 - 100). */
public record ChargeItemRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain only letters, digits, '.', '_' and '-'")
        String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal vatRate) {
}
