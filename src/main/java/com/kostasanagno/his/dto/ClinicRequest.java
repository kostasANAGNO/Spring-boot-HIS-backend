package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code specialtyId} is optional: set it when the clinic practises one of the listed specialties. */
public record ClinicRequest(@NotBlank @Size(max = 100) String name, Long specialtyId) {
}
