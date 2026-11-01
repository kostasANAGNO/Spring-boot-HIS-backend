package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.CancelInvoiceRequest;
import com.kostasanagno.his.dto.InvoiceCreateRequest;
import com.kostasanagno.his.dto.InvoiceLineRequest;
import com.kostasanagno.his.dto.InvoiceResponse;
import com.kostasanagno.his.dto.InvoiceSearchCriteria;
import com.kostasanagno.his.dto.InvoiceSummaryResponse;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.dto.PaymentRequest;
import com.kostasanagno.his.dto.PaymentResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Billing is done by ADMIN and RECEPTIONIST; every staff role can read. Invoices and payments are never deleted:
 * an invoice is cancelled (ADMIN), a wrong DRAFT line can be removed.
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    @PreAuthorize(AccessRules.BILLING_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.CREATE)
    @Operation(summary = "Create an empty DRAFT invoice for a patient (ADMIN, RECEPTIONIST)")
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceCreateRequest request) {
        return created(invoiceService.create(request));
    }

    @PostMapping("/from-hospitalization/{hospitalizationId}")
    @PreAuthorize(AccessRules.BILLING_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.CREATE)
    @Operation(summary = "DRAFT invoice for a discharged stay: one bed-day line per started 24 hours")
    public ResponseEntity<InvoiceResponse> fromHospitalization(@PathVariable Long hospitalizationId) {
        return created(invoiceService.createFromHospitalization(hospitalizationId));
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.VIEW)
    @Operation(summary = "Search invoices (paged, sortable, filterable)")
    public PageResponse<InvoiceSummaryResponse> search(
            @ModelAttribute InvoiceSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(invoiceService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Get an invoice with its lines")
    public InvoiceResponse getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Payments recorded on an invoice, oldest first")
    public List<PaymentResponse> payments(@PathVariable Long id) {
        return invoiceService.getPayments(id);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(AccessRules.BILLING_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.UPDATE, idArg = "id")
    @Operation(summary = "Add a price list entry to a DRAFT invoice (price and VAT are copied now)")
    public InvoiceResponse addLine(@PathVariable Long id, @Valid @RequestBody InvoiceLineRequest request) {
        return invoiceService.addLine(id, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize(AccessRules.BILLING_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.UPDATE, idArg = "id")
    @Operation(summary = "Remove a line from a DRAFT invoice")
    public InvoiceResponse removeLine(@PathVariable Long id, @PathVariable Long lineId) {
        return invoiceService.removeLine(id, lineId);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize(AccessRules.BILLING_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.ISSUE, idArg = "id")
    @Operation(summary = "Issue a DRAFT invoice: assigns its number and freezes it")
    public InvoiceResponse issue(@PathVariable Long id) {
        return invoiceService.issue(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.CANCEL, idArg = "id")
    @Operation(summary = "Cancel a DRAFT or unpaid ISSUED invoice (ADMIN only)")
    public InvoiceResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelInvoiceRequest request) {
        return invoiceService.cancel(id, request);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize(AccessRules.BILLING_STAFF)
    @Audited(resource = AuditResource.INVOICE, action = AuditAction.PAY, idArg = "id")
    @Operation(summary = "Record a payment on an ISSUED invoice (cannot exceed the amount due)")
    public InvoiceResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {
        return invoiceService.recordPayment(id, request, Caller.from(authentication));
    }

    private static ResponseEntity<InvoiceResponse> created(InvoiceResponse invoice) {
        var location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/invoices/{id}")
                .buildAndExpand(invoice.id()).toUri();
        return ResponseEntity.created(location).body(invoice);
    }
}
