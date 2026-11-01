package com.kostasanagno.his.controller;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.dto.AuditEventResponse;
import com.kostasanagno.his.dto.AuditEventSearchCriteria;
import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.security.AccessRules;
import com.kostasanagno.his.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only and ADMIN only. Reading the audit log is itself audited. There is no way to change or delete events. */
@RestController
@RequestMapping("/api/audit-events")
@RequiredArgsConstructor
@Tag(name = "Audit log")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize(AccessRules.ADMIN_ONLY)
    @Audited(resource = AuditResource.AUDIT_LOG, action = AuditAction.VIEW)
    @Operation(summary = "Search the audit log (ADMIN only; e.g. patientId=1 shows who accessed that patient)")
    public PageResponse<AuditEventResponse> search(
            @ModelAttribute AuditEventSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(auditService.search(criteria, pageable));
    }
}
