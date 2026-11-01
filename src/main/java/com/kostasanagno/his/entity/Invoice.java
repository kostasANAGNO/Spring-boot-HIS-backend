package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * An invoice for one patient. Lines can only change while DRAFT; once ISSUED the amounts, number and links are
 * frozen (also enforced by a database trigger). Never deleted.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", updatable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospitalization_id", updatable = false)
    private Hospitalization hospitalization;

    @ToString.Include
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    /** Assigned when the invoice is issued, e.g. {@code INV-2026-000042}. */
    @ToString.Include
    @Column(name = "invoice_number", length = 30)
    private String invoiceNumber;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Builder.Default
    @Column(name = "total_net", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(name = "total_vat", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVat = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(name = "total_gross", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    public BigDecimal getOutstandingAmount() {
        return totalGross.subtract(paidAmount);
    }
}
