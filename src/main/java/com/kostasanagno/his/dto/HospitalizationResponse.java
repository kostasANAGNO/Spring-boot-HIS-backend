package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.HospitalizationStatus;
import java.time.Instant;

/**
 * A stay: its life cycle plus the two documents. {@code admission} is always present, {@code discharge} only once the
 * patient has left.
 */
public record HospitalizationResponse(
        Long id,
        PatientSummary patient,
        DoctorSummary attendingDoctor,
        BedSummary currentBed,
        Long appointmentId,
        HospitalizationStatus status,
        Instant admissionTime,
        Instant dischargeTime,
        AdmissionRecordResponse admission,
        DischargeRecordResponse discharge,
        Instant createdAt,
        Instant updatedAt) {

    /** Static (not an instance wither) so MapStruct does not mistake it for a setter. */
    public static HospitalizationResponse assemble(HospitalizationResponse source, BedSummary bed,
            AdmissionRecordResponse admission, DischargeRecordResponse discharge) {
        return new HospitalizationResponse(source.id, source.patient, source.attendingDoctor, bed,
                source.appointmentId, source.status, source.admissionTime, source.dischargeTime, admission, discharge,
                source.createdAt, source.updatedAt);
    }

    /** Copy without clinical text (for users who must not see it). */
    public HospitalizationResponse withoutClinicalData() {
        return new HospitalizationResponse(id, patient, attendingDoctor, currentBed, appointmentId, status,
                admissionTime, dischargeTime, admission == null ? null : admission.withoutClinicalData(),
                discharge == null ? null : discharge.withoutClinicalData(), createdAt, updatedAt);
    }
}
