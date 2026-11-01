package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.HospitalizationStatus;
import java.time.Instant;

/**
 * All filters are optional. {@code clinicId}/{@code wardId}/{@code bedId} match the current bed (or the last bed for discharged
 * stays); the full movement history is available per hospitalization. {@code admittedFrom}/{@code admittedTo}
 * bound the admission time (both inclusive).
 */
public record HospitalizationSearchCriteria(
        Long patientId,
        Long doctorId,
        Long clinicId,
        Long wardId,
        Long bedId,
        HospitalizationStatus status,
        Instant admittedFrom,
        Instant admittedTo) {

    public HospitalizationSearchCriteria withDoctorId(Long newDoctorId) {
        return new HospitalizationSearchCriteria(patientId, newDoctorId, clinicId, wardId, bedId, status, admittedFrom, admittedTo);
    }
}
