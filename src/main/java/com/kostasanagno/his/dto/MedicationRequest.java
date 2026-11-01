package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.MedicationForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A catalog entry. {@code controlled} medications can only be dispensed by a pharmacist. */
public record MedicationRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain only letters, digits, '.', '_' and '-'")
        String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull MedicationForm form,
        @Size(max = 50) String strength,
        @Size(max = 10) @Pattern(regexp = "^[A-Za-z0-9]*$", message = "must be an ATC code such as N02BE01") String atcCode,
        boolean controlled) {
}
