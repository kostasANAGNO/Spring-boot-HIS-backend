package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** A clinic (department) of the hospital. It contains wards; doctors can belong to one. Deactivated, never deleted. */
@Entity
@Table(name = "clinics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Clinic extends BaseEntity {

    @ToString.Include
    @Column(nullable = false, length = 100)
    private String name;

    /** The medical specialty this clinic practises, when there is a matching one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
