package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A doctor's order to give one medication to a hospitalized patient. Never edited (the database refuses it): to change
 * a dose, stop the order and write a new one. Only {@code status} and the {@code closed*} fields move. Clinical data:
 * never log its contents.
 */
@Entity
@Table(name = "medication_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class MedicationOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospitalization_id", nullable = false, updatable = false)
    private Hospitalization hospitalization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false, updatable = false)
    private Medication medication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescribed_by_doctor_id", nullable = false, updatable = false)
    private Doctor prescribedBy;

    @Column(name = "dose_amount", nullable = false, precision = 10, scale = 3, updatable = false)
    private BigDecimal doseAmount;

    /** E.g. {@code mg}, {@code ml}, {@code tablet}. */
    @Column(name = "dose_unit", nullable = false, length = 20, updatable = false)
    private String doseUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MedicationRoute route;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20, updatable = false)
    private MedicationSchedule scheduleType;

    /** Hours between doses (SCHEDULED) or the minimum spacing (AS_NEEDED); null for ONCE. */
    @Column(name = "interval_hours", updatable = false)
    private Integer intervalHours;

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time", updatable = false)
    private Instant endTime;

    @Column(length = 1000, updatable = false)
    private String instructions;

    @ToString.Include
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedicationOrderStatus status = MedicationOrderStatus.PENDING;

    /** Why the order was rejected, cancelled or stopped. */
    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Who closed it; null when the system did (e.g. the patient was discharged). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;
}
