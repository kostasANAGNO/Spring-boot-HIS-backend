package com.kostasanagno.his.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Used for both create (POST) and full update (PUT). {@code userId} (login account) and {@code clinicId} are optional. */
public record DoctorRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "must contain only letters, digits and '-'")
        String licenseNumber,
        @NotNull Long specialtyId,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "is not a valid phone number") String phone,
        @Email @Size(max = 255) String email,
        Long userId,
        Long clinicId) {
}
