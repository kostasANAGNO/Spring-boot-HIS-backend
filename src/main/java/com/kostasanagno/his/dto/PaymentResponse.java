package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, BigDecimal amount, PaymentMethod method, Instant paidAt, String reference,
        String recordedBy, Instant createdAt) {
}
