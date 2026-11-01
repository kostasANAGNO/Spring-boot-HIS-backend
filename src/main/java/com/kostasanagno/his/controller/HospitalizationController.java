package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.AdmissionRequest;
import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.DischargeRequest;
import com.kostasanagno.his.dto.HospitalizationResponse;
import com.kostasanagno.his.dto.HospitalizationSearchCriteria;
import com.kostasanagno.his.dto.HospitalizationUpdateRequest;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.dto.TransferRequest;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.HospitalizationService;
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

/** There is deliberately no DELETE: hospitalizations and their bed history are kept. */
@RestController
@RequestMapping("/api/hospitalizations")
@RequiredArgsConstructor
@Tag(name = "Hospitalizations")
public class HospitalizationController {

    private final HospitalizationService hospitalizationService;

    @PostMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.ADMIT)
    @Operation(summary = "Admit a patient into a bed (409 if the bed is occupied or the patient already admitted)")
    public ResponseEntity<HospitalizationResponse> admit(@Valid @RequestBody AdmissionRequest request,
            Authentication authentication) {
        HospitalizationResponse created = hospitalizationService.admit(request, Caller.from(authentication));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize(AccessRules.DOCTOR_ONLY)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.VIEW)
    @Operation(summary = "Hospitalizations attended by the logged-in doctor (DOCTOR only)")
    public PageResponse<HospitalizationResponse> myHospitalizations(
            @ModelAttribute HospitalizationSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "admissionTime", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return PageResponse.from(
                hospitalizationService.searchForDoctorUser(criteria, pageable, Caller.from(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Get a hospitalization (clinical fields are hidden from receptionists)")
    public HospitalizationResponse getById(@PathVariable Long id, Authentication authentication) {
        return hospitalizationService.getById(id, Caller.from(authentication));
    }

    @GetMapping("/{id}/bed-history")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.VIEW, idArg = "id")
    @Operation(summary = "Every bed the patient occupied during this hospitalization, oldest first")
    public List<BedAssignmentResponse> bedHistory(@PathVariable Long id, Authentication authentication) {
        return hospitalizationService.getBedHistory(id, Caller.from(authentication));
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.VIEW)
    @Operation(summary = "Search hospitalizations (paged, sortable, filterable)")
    public PageResponse<HospitalizationResponse> search(
            @ModelAttribute HospitalizationSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "admissionTime", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return PageResponse.from(hospitalizationService.search(criteria, pageable, Caller.from(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_OR_DOCTOR)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.UPDATE, idArg = "id")
    @Operation(summary = "Update reason, diagnosis and attending doctor while ADMITTED (ADMIN, DOCTOR)")
    public HospitalizationResponse update(@PathVariable Long id,
            @Valid @RequestBody HospitalizationUpdateRequest request, Authentication authentication) {
        return hospitalizationService.update(id, request, Caller.from(authentication));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize(AccessRules.CLINICAL_STAFF)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.TRANSFER, idArg = "id")
    @Operation(summary = "Move the patient to another free bed (ADMIN, DOCTOR, NURSE)")
    public HospitalizationResponse transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        return hospitalizationService.transfer(id, request, Caller.from(authentication));
    }

    @PostMapping("/{id}/discharge")
    @PreAuthorize(AccessRules.ADMIN_OR_DOCTOR)
    @Audited(resource = AuditResource.HOSPITALIZATION, action = AuditAction.DISCHARGE, idArg = "id")
    @Operation(summary = "Discharge the patient (ADMIN or the attending DOCTOR)")
    public HospitalizationResponse discharge(@PathVariable Long id, @Valid @RequestBody DischargeRequest request,
            Authentication authentication) {
        return hospitalizationService.discharge(id, request, Caller.from(authentication));
    }
}
