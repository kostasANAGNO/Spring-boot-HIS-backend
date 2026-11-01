package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import java.time.Instant;

/** The note with its CURRENT (latest) version; see {@code ClinicalNoteVersionResponse} for the history. */
public record ClinicalNoteResponse(
        Long id,
        PatientSummary patient,
        DoctorSummary authorDoctor,
        Long appointmentId,
        Long hospitalizationId,
        int version,
        String history,
        String examination,
        String assessment,
        String plan,
        String diagnosisCode,
        String lastChangeReason,
        String editedBy,
        Instant createdAt,
        Instant modifiedAt) implements Auditable {

    @Override
    public Long auditResourceId() {
        return id;
    }

    @Override
    public Long auditPatientId() {
        return patient.id();
    }

}
