package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.ChargeItemRequest;
import com.kostasanagno.his.dto.ChargeItemResponse;
import com.kostasanagno.his.dto.ChargeItemSearchCriteria;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.ChargeItemService;
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

/** The price list. Read by everyone, maintained by ADMIN; entries are deactivated, never deleted. */
@RestController
@RequestMapping("/api/charge-items")
@RequiredArgsConstructor
@Tag(name = "Price list")
public class ChargeItemController {

    private final ChargeItemService chargeItemService;

    @PostMapping
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Add a price list entry (ADMIN only)")
    public ResponseEntity<ChargeItemResponse> create(@Valid @RequestBody ChargeItemRequest request) {
        ChargeItemResponse created = chargeItemService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Search the price list (paged, sortable; filters: text, active)")
    public PageResponse<ChargeItemResponse> search(
            @ModelAttribute ChargeItemSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(chargeItemService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Get a price list entry")
    public ChargeItemResponse getById(@PathVariable Long id) {
        return chargeItemService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Change a price list entry; existing invoices keep their copy (ADMIN only)")
    public ChargeItemResponse update(@PathVariable Long id, @Valid @RequestBody ChargeItemRequest request) {
        return chargeItemService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Remove an entry from the price list for new invoices (ADMIN only)")
    public ChargeItemResponse deactivate(@PathVariable Long id) {
        return chargeItemService.deactivate(id);
    }
}
