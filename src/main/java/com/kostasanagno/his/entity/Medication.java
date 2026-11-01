package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** An entry of the hospital's medication catalog. Deactivated, never deleted. */
@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Medication extends BaseEntity {

    @ToString.Include
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedicationForm form;

    /** E.g. {@code 500 mg} or {@code 100 IU/ml}. */
    @Column(length = 50)
    private String strength;

    /** Anatomical Therapeutic Chemical classification code, when known. */
    @Column(name = "atc_code", length = 10)
    private String atcCode;

    /** Controlled substances can only be dispensed by a pharmacist. */
    @Builder.Default
    @Column(nullable = false)
    private boolean controlled = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
