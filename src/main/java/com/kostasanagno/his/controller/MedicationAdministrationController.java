package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.AdministrationSearchCriteria;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.MedicationOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The medication administration record across orders (e.g. everything given to one patient). Read-only. */
@RestController
@RequestMapping("/api/medication-administrations")
@RequiredArgsConstructor
@Tag(name = "Medication administrations")
public class MedicationAdministrationController {

    private final MedicationOrderService orderService;

    @GetMapping
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Audited(resource = AuditResource.MEDICATION_ORDER, action = AuditAction.VIEW)
    @Operation(summary = "Search administrations (filters: hospitalizationId, orderId, patientId, outcome, from, to)")
    public PageResponse<AdministrationResponse> search(
            @ModelAttribute AdministrationSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "administeredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(orderService.searchAdministrations(criteria, pageable));
    }
}
