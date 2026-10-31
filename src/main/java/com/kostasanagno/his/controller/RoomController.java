package com.kostasanagno.his.controller;

import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.dto.RoomRequest;
import com.kostasanagno.his.dto.RoomResponse;
import com.kostasanagno.his.dto.RoomSearchCriteria;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.RoomService;
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
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Create a room (ADMIN only)")
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        RoomResponse created = roomService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Search rooms (paged, sortable; filters: wardId, active)")
    public PageResponse<RoomResponse> search(
            @ModelAttribute RoomSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "roomNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(roomService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(AccessRules.ANY_STAFF)
    @Operation(summary = "Get a room by id")
    public RoomResponse getById(@PathVariable Long id) {
        return roomService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Update a room (ADMIN only)")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Operation(summary = "Deactivate a room; rejected while any of its beds is occupied (ADMIN only)")
    public RoomResponse deactivate(@PathVariable Long id) {
        return roomService.deactivate(id);
    }
}
