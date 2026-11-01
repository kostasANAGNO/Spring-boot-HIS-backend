package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.InvoiceStatus;
import java.time.Instant;

/**
 * All filters are optional. {@code invoiceNumber} is a contains match; {@code outstanding=true} keeps ISSUED invoices
 * that are not fully paid yet. {@code createdFrom}/{@code createdTo} bound the creation time (inclusive).
 */
public record InvoiceSearchCriteria(
        Long patientId,
        InvoiceStatus status,
        String invoiceNumber,
        Boolean outstanding,
        Instant createdFrom,
        Instant createdTo) {
}
