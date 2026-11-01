package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.ClinicalNoteAmendRequest;
import com.kostasanagno.his.dto.ClinicalNoteRequest;
import com.kostasanagno.his.dto.ClinicalNoteResponse;
import com.kostasanagno.his.dto.ClinicalNoteSearchCriteria;
import com.kostasanagno.his.dto.ClinicalNoteVersionResponse;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.ClinicalNoteService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Medical records are clinical data: receptionists get 403 on every endpoint. There is deliberately no DELETE;
 * a correction is an amendment that adds a new version.
 */
@RestController
@RequestMapping("/api/clinical-notes")
@RequiredArgsConstructor
@Tag(name = "Clinical notes")
public class ClinicalNoteController {

    private final ClinicalNoteService clinicalNoteService;

    @PostMapping
    @PreAuthorize(AccessRules.DOCTOR_ONLY)
    @Operation(summary = "Write a clinical note as the logged-in doctor (DOCTOR only)")
    public ResponseEntity<ClinicalNoteResponse> create(@Valid @RequestBody ClinicalNoteRequest request,
            Authentication authentication) {
        ClinicalNoteResponse created = clinicalNoteService.create(request, Caller.from(authentication));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize(AccessRules.DOCTOR_ONLY)
    @Operation(summary = "Notes written by the logged-in doctor (DOCTOR only)")
    public PageResponse<ClinicalNoteResponse> myNotes(
            @ModelAttribute ClinicalNoteSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return PageResponse.from(
                clinicalNoteService.searchForDoctorUser(criteria, pageable, Caller.from(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.CLINICAL_STAFF)
    @Operation(summary = "Get a clinical note with its current version (ADMIN, DOCTOR, NURSE)")
    public ClinicalNoteResponse getById(@PathVariable Long id) {
        return clinicalNoteService.getById(id);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(AccessRules.CLINICAL_STAFF)
    @Operation(summary = "Every version of a note, oldest first (ADMIN, DOCTOR, NURSE)")
    public List<ClinicalNoteVersionResponse> versions(@PathVariable Long id) {
        return clinicalNoteService.getVersions(id);
    }

    @GetMapping
    @PreAuthorize(AccessRules.CLINICAL_STAFF)
    @Operation(summary = "Search clinical notes (paged, sortable, filterable; ADMIN, DOCTOR, NURSE)")
    public PageResponse<ClinicalNoteResponse> search(
            @ModelAttribute ClinicalNoteSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(clinicalNoteService.search(criteria, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_OR_DOCTOR)
    @Operation(summary = "Amend a note: adds a new version (the author or an admin)")
    public ClinicalNoteResponse amend(@PathVariable Long id, @Valid @RequestBody ClinicalNoteAmendRequest request,
            Authentication authentication) {
        return clinicalNoteService.amend(id, request, Caller.from(authentication));
    }
}
