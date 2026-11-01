package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.HospitalizationStatus;
import java.time.Instant;

public record HospitalizationResponse(
        Long id,
        PatientSummary patient,
        DoctorSummary attendingDoctor,
        BedSummary currentBed,
        Long appointmentId,
        HospitalizationStatus status,
        Instant admissionTime,
        Instant dischargeTime,
        String admissionReason,
        String diagnosis,
        String dischargeNotes,
        Instant createdAt,
        Instant updatedAt) implements Auditable {

    @Override
    public Long auditResourceId() {
        return id;
    }

    @Override
    public Long auditPatientId() {
        return patient.id();
    }


    /** Static (not an instance wither) so MapStruct does not mistake it for a setter. */
    public static HospitalizationResponse withCurrentBed(HospitalizationResponse source, BedSummary bed) {
        return new HospitalizationResponse(source.id, source.patient, source.attendingDoctor, bed,
                source.appointmentId, source.status, source.admissionTime, source.dischargeTime,
                source.admissionReason, source.diagnosis, source.dischargeNotes, source.createdAt, source.updatedAt);
    }

    /** Copy without clinical text (for users who must not see it). */
    public HospitalizationResponse withoutClinicalData() {
        return new HospitalizationResponse(id, patient, attendingDoctor, currentBed, appointmentId, status,
                admissionTime, dischargeTime, admissionReason, null, null, createdAt, updatedAt);
    }
}
