package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A ward (wing) of a clinic. The name must be unique inside its clinic. */
public record WardRequest(@NotNull Long clinicId, @NotBlank @Size(max = 100) String name) {
}
