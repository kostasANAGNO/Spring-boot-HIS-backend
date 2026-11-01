package com.kostasanagno.his.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * An amendment creates a new version holding exactly these contents (earlier versions are kept).
 * {@code baseVersion} is the version the author was looking at; a different current version means someone else
 * amended in the meantime (409).
 */
public record ClinicalNoteAmendRequest(
        @NotNull Integer baseVersion,
        @NotBlank @Size(max = 500) String changeReason,
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
