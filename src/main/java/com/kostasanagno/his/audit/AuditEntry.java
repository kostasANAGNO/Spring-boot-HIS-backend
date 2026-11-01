package com.kostasanagno.his.audit;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import java.util.List;

/** Everything about one audited request; the service turns it into one stored event per target. */
public record AuditEntry(
        String username,
        String roles,
        AuditAction action,
        AuditResource resource,
        AuditOutcome outcome,
        String httpMethod,
        String path,
        String ipAddress,
        List<AuditTarget> targets) {
}
