package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.BedRequest;
import com.kostasanagno.his.dto.BedResponse;
import com.kostasanagno.his.dto.BedSearchCriteria;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.BedService;
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

@RestController
@RequestMapping("/api/beds")
@RequiredArgsConstructor
@Tag(name = "Beds")
public class BedController {

    private final BedService bedService;

    @PostMapping
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Create a bed (ADMIN only)")
    public ResponseEntity<BedResponse> create(@Valid @RequestBody BedRequest request) {
        BedResponse created = bedService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Search beds (paged, sortable; filters: wardId, roomId, available, active)")
    public PageResponse<BedResponse> search(
            @ModelAttribute BedSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "bedNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(bedService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Get a bed by id")
    public BedResponse getById(@PathVariable Long id) {
        return bedService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Update a bed (ADMIN only)")
    public BedResponse update(@PathVariable Long id, @Valid @RequestBody BedRequest request) {
        return bedService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Deactivate a bed; rejected while it is occupied (ADMIN only)")
    public BedResponse deactivate(@PathVariable Long id) {
        return bedService.deactivate(id);
    }
}
