package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.AdministrationOutcome;
import java.math.BigDecimal;
import java.time.Instant;

public record AdministrationResponse(
        Long id,
        Long orderId,
        Long hospitalizationId,
        Long patientId,
        AdministrationOutcome outcome,
        Instant administeredAt,
        BigDecimal doseAmount,
        String notes,
        String administeredBy,
        Instant createdAt) implements Auditable {

    /** The audited record is the order this entry belongs to. */
    @Override
    public Long auditResourceId() {
        return orderId;
    }

    @Override
    public Long auditPatientId() {
        return patientId;
    }
}
