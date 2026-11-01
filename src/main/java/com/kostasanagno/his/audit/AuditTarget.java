package com.kostasanagno.his.audit;

/** One record touched by a request. */
public record AuditTarget(Long resourceId, Long patientId) {
}
