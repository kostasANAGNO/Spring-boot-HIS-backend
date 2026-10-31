package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** History row: a hospitalization occupied a bed from {@code startTime} until {@code endTime} (null = still there). */
@Entity
@Table(name = "bed_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class BedAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospitalization_id", nullable = false)
    private Hospitalization hospitalization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bed_id", nullable = false)
    private Bed bed;

    @ToString.Include
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @ToString.Include
    @Column(name = "end_time")
    private Instant endTime;

    /** Why the patient was moved here (transfers only); may be clinical, so hidden from receptionists. */
    @Column(length = 255)
    private String reason;
}
