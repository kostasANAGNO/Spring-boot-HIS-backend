package com.kostasanagno.his.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Adds a price list entry to a DRAFT invoice; price and VAT are copied from the price list at this moment. */
public record InvoiceLineRequest(@NotNull Long chargeItemId, @NotNull @Min(1) @Max(9999) Integer quantity) {
}
