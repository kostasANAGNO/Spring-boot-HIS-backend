package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import java.time.Instant;

public record AuditEventResponse(
        Long id,
        Instant occurredAt,
        String username,
        String roles,
        AuditAction action,
        AuditResource resourceType,
        Long resourceId,
        Long patientId,
        AuditOutcome outcome,
        String httpMethod,
        String path,
        String ipAddress) {
}
