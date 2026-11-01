package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.CancelInvoiceRequest;
import com.kostasanagno.his.dto.InvoiceCreateRequest;
import com.kostasanagno.his.dto.InvoiceLineRequest;
import com.kostasanagno.his.dto.InvoiceResponse;
import com.kostasanagno.his.dto.InvoiceSearchCriteria;
import com.kostasanagno.his.dto.InvoiceSummaryResponse;
import com.kostasanagno.his.dto.PaymentRequest;
import com.kostasanagno.his.dto.PaymentResponse;
import com.kostasanagno.his.security.Caller;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Invoice life cycle: DRAFT (lines can change) -> ISSUED (numbered and frozen) -> PAID, or CANCELLED. Invoices and
 * payments are never deleted. Every operation that changes an invoice locks its row first.
 */
public interface InvoiceService {

    InvoiceResponse create(InvoiceCreateRequest request);

    /** A DRAFT invoice for a discharged stay with one bed-day line per started 24 hours of the stay. */
    InvoiceResponse createFromHospitalization(Long hospitalizationId);

    InvoiceResponse getById(Long id);

    Page<InvoiceSummaryResponse> search(InvoiceSearchCriteria criteria, Pageable pageable);

    List<PaymentResponse> getPayments(Long id);

    InvoiceResponse addLine(Long id, InvoiceLineRequest request);

    InvoiceResponse removeLine(Long id, Long lineId);

    /** Assigns the invoice number and freezes the invoice. Needs at least one line. */
    InvoiceResponse issue(Long id);

    /** Only DRAFT or unpaid ISSUED invoices; a reason is required. */
    InvoiceResponse cancel(Long id, CancelInvoiceRequest request);

    /** Records a payment on an ISSUED invoice; the invoice becomes PAID when nothing is left to pay. */
    InvoiceResponse recordPayment(Long id, PaymentRequest request, Caller caller);
}
