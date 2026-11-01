package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The life cycle of a stay (patient, doctor, status, times). What was said at the start and the end lives in
 * {@link AdmissionRecord} and {@link DischargeRecord}. Never deleted.
 */
@Entity
@Table(name = "hospitalizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Hospitalization extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attending_doctor_id", nullable = false)
    private Doctor attendingDoctor;

    /** Optional link; the appointment itself is never modified by the hospitalization. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @ToString.Include
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HospitalizationStatus status = HospitalizationStatus.ADMITTED;

    @ToString.Include
    @Column(name = "admission_time", nullable = false)
    private Instant admissionTime;

    @Column(name = "discharge_time")
    private Instant dischargeTime;

}
