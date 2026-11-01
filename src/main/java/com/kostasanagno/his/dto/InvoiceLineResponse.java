package com.kostasanagno.his.dto;

import java.math.BigDecimal;

public record InvoiceLineResponse(
        Long id,
        Long chargeItemId,
        String code,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount) {
}
