package com.kostasanagno.his.dto;

import java.math.BigDecimal;

public record ChargeItemResponse(Long id, String code, String name, BigDecimal unitPrice, BigDecimal vatRate,
        boolean active) {
}
