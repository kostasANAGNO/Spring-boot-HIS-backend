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

/** Never deleted. {@code diagnosis} and {@code dischargeNotes} are clinical data: never log or print them. */
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

    @Column(name = "admission_reason", nullable = false, length = 500)
    private String admissionReason;

    @Column(length = 2000)
    private String diagnosis;

    @Column(name = "discharge_notes", length = 2000)
    private String dischargeNotes;
}
