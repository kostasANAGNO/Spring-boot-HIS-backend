package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.AdministrationRequest;
import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.DispenseRequest;
import com.kostasanagno.his.dto.DispensingResponse;
import com.kostasanagno.his.dto.MedicationOrderCloseRequest;
import com.kostasanagno.his.dto.MedicationOrderRequest;
import com.kostasanagno.his.dto.MedicationOrderResponse;
import com.kostasanagno.his.dto.MedicationOrderSearchCriteria;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.MedicationOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Medication for hospitalized patients. Receptionists have no access at all (it is clinical data); pharmacists see the
 * orders but no diagnoses. There is no edit and no delete: an order is stopped and a new one written.
 */
@RestController
@RequestMapping("/api/medication-orders")
@RequiredArgsConstructor
@Tag(name = "Medication orders")
public class MedicationOrderController {

    private final MedicationOrderService orderService;

    @PostMapping
    @PreAuthorize(AccessRules.DOCTOR_ONLY)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.CREATE)
    @Operation(summary = "Order a medication for an admitted patient (DOCTOR only)")
    public ResponseEntity<MedicationOrderResponse> create(@Valid @RequestBody MedicationOrderRequest request,
            Authentication authentication) {
        MedicationOrderResponse created = orderService.create(request, Caller.from(authentication));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize(AccessRules.DOCTOR_ONLY)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.VIEW)
    @Operation(summary = "Orders written by the logged-in doctor (DOCTOR only)")
    public PageResponse<MedicationOrderResponse> myOrders(
            @ModelAttribute MedicationOrderSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return PageResponse.from(orderService.searchForDoctorUser(criteria, pageable, Caller.from(authentication)));
    }

    @GetMapping
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.VIEW)
    @Operation(summary = "Search orders (the pharmacy queue is status=PENDING)")
    public PageResponse<MedicationOrderResponse> search(
            @ModelAttribute MedicationOrderSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(orderService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Get an order")
    public MedicationOrderResponse getById(@PathVariable Long id) {
        return orderService.getById(id);
    }

    @PostMapping("/{id}/dispense")
    @PreAuthorize(AccessRules.PHARMACY_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.DISPENSE, idArg = "id")
    @Operation(summary = "Record a supply for the order; controlled medication needs a pharmacist")
    public MedicationOrderResponse dispense(@PathVariable Long id, @Valid @RequestBody DispenseRequest request,
            Authentication authentication) {
        return orderService.dispense(id, request, Caller.from(authentication));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(AccessRules.PHARMACY_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.REJECT, idArg = "id")
    @Operation(summary = "The pharmacy refuses a PENDING order, with a reason")
    public MedicationOrderResponse reject(@PathVariable Long id, @Valid @RequestBody MedicationOrderCloseRequest request,
            Authentication authentication) {
        return orderService.reject(id, request, Caller.from(authentication));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(AccessRules.ADMIN_OR_DOCTOR)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.CANCEL, idArg = "id")
    @Operation(summary = "Withdraw a PENDING order (ADMIN, DOCTOR)")
    public MedicationOrderResponse cancel(@PathVariable Long id, @Valid @RequestBody MedicationOrderCloseRequest request,
            Authentication authentication) {
        return orderService.cancel(id, request, Caller.from(authentication));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize(AccessRules.ADMIN_OR_DOCTOR)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.STOP, idArg = "id")
    @Operation(summary = "Stop a DISPENSED order (ADMIN, DOCTOR)")
    public MedicationOrderResponse stop(@PathVariable Long id, @Valid @RequestBody MedicationOrderCloseRequest request,
            Authentication authentication) {
        return orderService.stop(id, request, Caller.from(authentication));
    }

    @PostMapping("/{id}/administrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AccessRules.ADMINISTER_MEDICATION)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.ADMINISTER, idArg = "id")
    @Operation(summary = "Record a dose given, refused or held (DOCTOR, NURSE)")
    public AdministrationResponse administer(@PathVariable Long id, @Valid @RequestBody AdministrationRequest request,
            Authentication authentication) {
        return orderService.administer(id, request, Caller.from(authentication));
    }

    @GetMapping("/{id}/dispensings")
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Supplies handed out for the order, oldest first")
    public List<DispensingResponse> dispensings(@PathVariable Long id) {
        return orderService.getDispensings(id);
    }

    @GetMapping("/{id}/administrations")
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "The administration record of the order, oldest first")
    public List<AdministrationResponse> administrations(@PathVariable Long id) {
        return orderService.getAdministrations(id);
    }
}
