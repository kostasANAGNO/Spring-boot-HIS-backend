package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AdmissionSource;
import com.kostasanagno.his.entity.AdmissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Admits a patient and writes the admission record. {@code admissionTime} defaults to now and must not be in the
 * future. {@code referringFacility} is required for {@code TRANSFER_IN}; {@code admissionSource} is
 * {@code APPOINTMENT} exactly when {@code appointmentId} is given. The initial diagnosis code is ICD-10 (e.g. I20.9).
 */
public record AdmissionRequest(
        @NotNull Long patientId,
        @NotNull Long attendingDoctorId,
        @NotNull Long bedId,
        @NotNull AdmissionType admissionType,
        @NotNull AdmissionSource admissionSource,
        @Size(max = 150) String referringFacility,
        @NotBlank @Size(max = 500) String admissionReason,
        @Pattern(regexp = "^[A-Za-z][0-9]{2}(\\.[0-9A-Za-z]{1,4})?$", message = "must be an ICD-10 code such as I20.9")
        String initialDiagnosisCode,
        @Size(max = 2000) String initialDiagnosis,
        Instant admissionTime,
        Long appointmentId) {
}
