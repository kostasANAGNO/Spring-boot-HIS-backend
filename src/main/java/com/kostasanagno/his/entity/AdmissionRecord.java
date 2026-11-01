package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The admission record ("eisitirio"): why, how and from where the patient came, and the initial diagnosis. Exactly
 * one per hospitalization, written together with it. It can be corrected while the patient is admitted and is frozen
 * after the discharge. The diagnosis is clinical data: never log or print it.
 */
@Entity
@Table(name = "admission_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class AdmissionRecord extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospitalization_id", nullable = false, updatable = false, unique = true)
    private Hospitalization hospitalization;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", nullable = false, length = 20, updatable = false)
    private AdmissionType admissionType;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "admission_source", nullable = false, length = 30, updatable = false)
    private AdmissionSource admissionSource;

    /** Where the patient came from; required for {@code TRANSFER_IN}. */
    @Column(name = "referring_facility", length = 150, updatable = false)
    private String referringFacility;

    @Column(nullable = false, length = 500)
    private String reason;

    /** ICD-10 style code, e.g. {@code I20.9}. */
    @Column(name = "initial_diagnosis_code", length = 10)
    private String initialDiagnosisCode;

    @Column(name = "initial_diagnosis", length = 2000)
    private String initialDiagnosis;
}
