package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.DischargeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Discharges the patient and writes the discharge record. {@code dischargeTime} defaults to now; it must be after the
 * last bed assignment started and not in the future. {@code destinationFacility} is required for
 * {@code TRANSFER_OUT} and not allowed otherwise; a {@code DECEASED} discharge has no {@code followUpDate}, and any
 * follow-up date must not be in the past.
 */
public record DischargeRequest(
        @NotNull DischargeType dischargeType,
        @Size(max = 150) String destinationFacility,
        @Pattern(regexp = "^[A-Za-z][0-9]{2}(\\.[0-9A-Za-z]{1,4})?$", message = "must be an ICD-10 code such as I20.9")
        String finalDiagnosisCode,
        @Size(max = 2000) String finalDiagnosis,
        @NotBlank @Size(max = 2000) String summary,
        @Size(max = 2000) String instructions,
        LocalDate followUpDate,
        Instant dischargeTime) {
}
