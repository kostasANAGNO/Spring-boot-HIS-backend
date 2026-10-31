package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.WardRequest;
import com.kostasanagno.his.dto.WardResponse;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.WardService;
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

@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
@Tag(name = "Wards")
public class WardController {

    private final WardService wardService;

    @PostMapping
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Create a ward (ADMIN only)")
    public ResponseEntity<WardResponse> create(@Valid @RequestBody WardRequest request) {
        WardResponse created = wardService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "List wards (active ones unless active=false)")
    public List<WardResponse> findAll(@RequestParam(required = false) Boolean active) {
        return wardService.findAll(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Get a ward by id")
    public WardResponse getById(@PathVariable Long id) {
        return wardService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Rename a ward (ADMIN only)")
    public WardResponse update(@PathVariable Long id, @Valid @RequestBody WardRequest request) {
        return wardService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Deactivate a ward; rejected while any of its beds is occupied (ADMIN only)")
    public WardResponse deactivate(@PathVariable Long id) {
        return wardService.deactivate(id);
    }
}
