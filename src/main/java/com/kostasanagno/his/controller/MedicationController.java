package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.MedicationRequest;
import com.kostasanagno.his.dto.MedicationResponse;
import com.kostasanagno.his.dto.MedicationSearchCriteria;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.MedicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** The medication catalog: read by those who handle medication, maintained by the pharmacy. */
@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
@Tag(name = "Medication catalog")
public class MedicationController {

    private final MedicationService medicationService;

    @PostMapping
    @PreAuthorize(AccessRules.PHARMACY_STAFF)
    @Operation(summary = "Add a medication to the catalog (ADMIN, PHARMACIST)")
    public ResponseEntity<MedicationResponse> create(@Valid @RequestBody MedicationRequest request) {
        MedicationResponse created = medicationService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Operation(summary = "Search the catalog (paged, sortable; filters: text, active)")
    public PageResponse<MedicationResponse> search(
            @ModelAttribute MedicationSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(medicationService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.MEDICATION_STAFF)
    @Operation(summary = "Get a catalog entry")
    public MedicationResponse getById(@PathVariable Long id) {
        return medicationService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.PHARMACY_STAFF)
    @Operation(summary = "Change a catalog entry (ADMIN, PHARMACIST)")
    public MedicationResponse update(@PathVariable Long id, @Valid @RequestBody MedicationRequest request) {
        return medicationService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(AccessRules.PHARMACY_STAFF)
    @Operation(summary = "Remove an entry from the catalog for new orders (ADMIN, PHARMACIST)")
    public MedicationResponse deactivate(@PathVariable Long id) {
        return medicationService.deactivate(id);
    }
}
