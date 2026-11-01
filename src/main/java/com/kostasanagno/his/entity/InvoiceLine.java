package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Snapshot of a charge item at the moment it was added: later price list changes do not touch it. */
@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_item_id", nullable = false, updatable = false)
    private ChargeItem chargeItem;

    @ToString.Include
    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(nullable = false, length = 150, updatable = false)
    private String description;

    @ToString.Include
    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal vatRate;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal netAmount;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal vatAmount;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal grossAmount;
}
