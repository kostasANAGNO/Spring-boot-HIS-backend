package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** An invoice without its lines, for lists. */
public record InvoiceSummaryResponse(
        Long id,
        String invoiceNumber,
        InvoiceStatus status,
        PatientSummary patient,
        Long appointmentId,
        Long hospitalizationId,
        BigDecimal totalGross,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        Instant issuedAt,
        Instant createdAt) implements Auditable {

    @Override
    public Long auditResourceId() {
        return id;
    }

    @Override
    public Long auditPatientId() {
        return patient.id();
    }
}
