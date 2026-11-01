package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.dto.PatientRequest;
import com.kostasanagno.his.dto.PatientResponse;
import com.kostasanagno.his.dto.PatientSearchCriteria;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patients")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.PATIENT, action = AuditAction.CREATE)
    @Operation(summary = "Register a patient")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        PatientResponse created = patientService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.PATIENT, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Get a patient by id")
    public PatientResponse getById(@PathVariable Long id) {
        return patientService.getById(id);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.PATIENT, action = AuditAction.VIEW)
    @Operation(summary = "Search patients (paged, sortable, filterable)")
    public PageResponse<PatientResponse> search(
            @ModelAttribute PatientSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(patientService.search(criteria, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.PATIENT, action = AuditAction.UPDATE, idArg = "id")
    @Operation(summary = "Update a patient")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return patientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Audited(resource = AuditResource.PATIENT, action = AuditAction.DELETE, idArg = "id")
    @Operation(summary = "Soft-delete a patient (ADMIN only)")
    public void delete(@PathVariable Long id) {
        patientService.delete(id);
    }
}
