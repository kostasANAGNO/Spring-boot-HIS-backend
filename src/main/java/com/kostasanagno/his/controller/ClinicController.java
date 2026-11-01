package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.ClinicRequest;
import com.kostasanagno.his.dto.ClinicResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.ClinicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Clinics (departments) of the hospital: read by everyone, maintained by ADMIN, deactivated and never deleted. */
@RestController
@RequestMapping("/api/clinics")
@RequiredArgsConstructor
@Tag(name = "Clinics")
public class ClinicController {

    private final ClinicService clinicService;

    @PostMapping
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Create a clinic (ADMIN only)")
    public ResponseEntity<ClinicResponse> create(@Valid @RequestBody ClinicRequest request) {
        ClinicResponse created = clinicService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "List clinics (active ones unless active=false)")
    public List<ClinicResponse> findAll(@RequestParam(required = false) Boolean active) {
        return clinicService.findAll(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Get a clinic by id")
    public ClinicResponse getById(@PathVariable Long id) {
        return clinicService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Rename a clinic or change its specialty (ADMIN only)")
    public ClinicResponse update(@PathVariable Long id, @Valid @RequestBody ClinicRequest request) {
        return clinicService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Deactivate a clinic; rejected while any of its beds is occupied (ADMIN only)")
    public ClinicResponse deactivate(@PathVariable Long id) {
        return clinicService.deactivate(id);
    }
}
