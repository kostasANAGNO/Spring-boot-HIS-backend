package com.kostasanagno.his.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * A new clinical note. At least one content field is required. Link it to at most one of an appointment or a
 * hospitalization of the same patient. {@code diagnosisCode} is an ICD-10 style code such as {@code I20.9}.
 */
public record ClinicalNoteRequest(
        @NotNull Long patientId,
        Long appointmentId,
        Long hospitalizationId,
        @Size(max = 4000) String history,
        @Size(max = 4000) String examination,
        @Size(max = 4000) String assessment,
        @Size(max = 4000) String plan,
        @Pattern(regexp = "^[A-Za-z][0-9]{2}(\\.[0-9A-Za-z]{1,4})?$", message = "must be an ICD-10 code such as I20.9")
        String diagnosisCode) {

    @JsonIgnore
    @AssertTrue(message = "at least one of history, examination, assessment, plan or diagnosisCode is required")
    public boolean isContentProvided() {
        return StringUtils.hasText(history) || StringUtils.hasText(examination) || StringUtils.hasText(assessment)
                || StringUtils.hasText(plan) || StringUtils.hasText(diagnosisCode);
    }
}
