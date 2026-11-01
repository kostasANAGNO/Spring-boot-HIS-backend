package com.kostasanagno.his.dto;

import java.time.Instant;

/**
 * All filters are optional. {@code diagnosisCode} matches the start of the CURRENT version's code
 * (e.g. {@code I20} finds I20.0, I20.9, ...). {@code createdFrom}/{@code createdTo} bound the note's creation time.
 */
public record ClinicalNoteSearchCriteria(
        Long patientId,
        Long authorDoctorId,
        Long appointmentId,
        Long hospitalizationId,
        String diagnosisCode,
        Instant createdFrom,
        Instant createdTo) {

    public ClinicalNoteSearchCriteria withAuthorDoctorId(Long newAuthorDoctorId) {
        return new ClinicalNoteSearchCriteria(patientId, newAuthorDoctorId, appointmentId, hospitalizationId,
                diagnosisCode, createdFrom, createdTo);
    }
}
