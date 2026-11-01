package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import java.time.Instant;

/** All filters are optional; {@code from}/{@code to} bound the event time (both inclusive). */
public record AuditEventSearchCriteria(
        String username,
        Long patientId,
        AuditResource resourceType,
        Long resourceId,
        AuditAction action,
        AuditOutcome outcome,
        Instant from,
        Instant to) {
}
