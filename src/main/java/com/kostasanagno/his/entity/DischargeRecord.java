package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The discharge record ("exitirio"): how the stay ended, the final diagnosis, the summary and the instructions for
 * after the stay. Exactly one per discharged hospitalization, written with the discharge and final from then on
 * (the database refuses any change). Everything but the type and the follow-up date is clinical data: never log it.
 */
@Entity
@Table(name = "discharge_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class DischargeRecord extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospitalization_id", nullable = false, updatable = false, unique = true)
    private Hospitalization hospitalization;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "discharge_type", nullable = false, length = 30, updatable = false)
    private DischargeType dischargeType;

    /** Where the patient was transferred to; required for {@code TRANSFER_OUT}, not allowed otherwise. */
    @Column(name = "destination_facility", length = 150, updatable = false)
    private String destinationFacility;

    @Column(name = "final_diagnosis_code", length = 10, updatable = false)
    private String finalDiagnosisCode;

    @Column(name = "final_diagnosis", length = 2000, updatable = false)
    private String finalDiagnosis;

    @Column(nullable = false, length = 2000, updatable = false)
    private String summary;

    @Column(length = 2000, updatable = false)
    private String instructions;

    @Column(name = "follow_up_date", updatable = false)
    private LocalDate followUpDate;
}
