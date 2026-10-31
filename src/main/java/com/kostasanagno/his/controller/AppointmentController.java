package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.AppointmentRequest;
import com.kostasanagno.his.dto.AppointmentResponse;
import com.kostasanagno.his.dto.AppointmentSearchCriteria;
import com.kostasanagno.his.dto.AppointmentUpdateRequest;
import com.kostasanagno.his.dto.CancelAppointmentRequest;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** There is deliberately no DELETE: cancelling keeps the history. */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments")
public class AppointmentController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Book an appointment (409 if the doctor or the patient is already booked)")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse created = appointmentService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize(AccessRules.DOCTOR_ONLY)
    @Operation(summary = "Appointments of the logged-in doctor (DOCTOR only)")
    public PageResponse<AppointmentResponse> myAppointments(
            Authentication authentication,
            @ModelAttribute AppointmentSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(appointmentService.searchForDoctorUser(authentication.getName(), criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Get an appointment by id")
    public AppointmentResponse getById(@PathVariable Long id) {
        return appointmentService.getById(id);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Search appointments (paged, sortable, filterable)")
    public PageResponse<AppointmentResponse> search(
            @ModelAttribute AppointmentSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(appointmentService.search(criteria, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Update/reschedule a SCHEDULED appointment")
    public AppointmentResponse update(@PathVariable Long id, @Valid @RequestBody AppointmentUpdateRequest request) {
        return appointmentService.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Cancel a SCHEDULED appointment (frees the slot)")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelAppointmentRequest request) {
        return appointmentService.cancel(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(AccessRules.COMPLETE_APPOINTMENT)
    @Operation(summary = "Complete a SCHEDULED appointment (ADMIN or the assigned DOCTOR)")
    public AppointmentResponse complete(@PathVariable Long id, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
        return appointmentService.complete(id, authentication.getName(), admin);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize(AccessRules.MARK_NO_SHOW)
    @Operation(summary = "Mark a SCHEDULED appointment as no-show")
    public AppointmentResponse noShow(@PathVariable Long id) {
        return appointmentService.markNoShow(id);
    }
}
