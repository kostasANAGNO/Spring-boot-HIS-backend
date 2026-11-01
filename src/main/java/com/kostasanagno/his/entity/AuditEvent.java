package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** One row = one access to or change of one record. Append-only (the database refuses UPDATE/DELETE). */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class AuditEvent extends IdentifiedEntity {

    @ToString.Include
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @ToString.Include
    @Column(nullable = false, length = 50, updatable = false)
    private String username;

    /** Comma separated roles of the user at that moment. */
    @Column(nullable = false, length = 100, updatable = false)
    private String roles;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AuditAction action;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30, updatable = false)
    private AuditResource resourceType;

    /** Null when the request named no single record (e.g. a search that found nothing, or a failed create). */
    @ToString.Include
    @Column(name = "resource_id", updatable = false)
    private Long resourceId;

    /** The patient whose data this concerns, when known. No foreign key: the log must outlive any data change. */
    @Column(name = "patient_id", updatable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "http_method", nullable = false, length = 10, updatable = false)
    private String httpMethod;

    /** Path only, never the query string (filters may contain personal data). */
    @Column(nullable = false, length = 255, updatable = false)
    private String path;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;
}
