package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.MedicationOrderStatus;

/** All filters are optional. The pharmacy's work queue is {@code status=PENDING}. */
public record MedicationOrderSearchCriteria(
        Long hospitalizationId,
        Long patientId,
        Long medicationId,
        Long prescribedByDoctorId,
        MedicationOrderStatus status) {

    public MedicationOrderSearchCriteria withPrescribedByDoctorId(Long doctorId) {
        return new MedicationOrderSearchCriteria(hospitalizationId, patientId, medicationId, doctorId, status);
    }
}
