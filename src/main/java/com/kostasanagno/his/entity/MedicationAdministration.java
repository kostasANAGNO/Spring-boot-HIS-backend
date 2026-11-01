package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One entry of the medication administration record (what happened at the bedside): a dose given, refused or held.
 * Append-only. {@code notes} are clinical: never log them.
 */
@Entity
@Table(name = "medication_administrations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class MedicationAdministration extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private MedicationOrder order;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AdministrationOutcome outcome;

    @ToString.Include
    @Column(name = "administered_at", nullable = false, updatable = false)
    private Instant administeredAt;

    /** The dose actually given; set exactly when the outcome is GIVEN. */
    @Column(name = "dose_amount", precision = 10, scale = 3, updatable = false)
    private BigDecimal doseAmount;

    @Column(length = 1000, updatable = false)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administered_by_user_id", nullable = false, updatable = false)
    private User administeredBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
