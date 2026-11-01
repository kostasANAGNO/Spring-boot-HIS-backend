package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corrects the admission record (reason, initial diagnosis) and the attending doctor while the patient is admitted. */
public record HospitalizationUpdateRequest(
        @NotNull Long attendingDoctorId,
        @NotBlank @Size(max = 500) String admissionReason,
        @Pattern(regexp = "^[A-Za-z][0-9]{2}(\\.[0-9A-Za-z]{1,4})?$", message = "must be an ICD-10 code such as I20.9")
        String initialDiagnosisCode,
        @Size(max = 2000) String initialDiagnosis) {
}
