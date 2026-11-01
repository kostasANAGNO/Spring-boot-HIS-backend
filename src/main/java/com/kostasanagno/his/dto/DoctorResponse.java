package com.kostasanagno.his.dto;

import java.time.Instant;

public record DoctorResponse(
        Long id,
        String firstName,
        String lastName,
        String licenseNumber,
        SpecialtyResponse specialty,
        ClinicSummary clinic,
        String phone,
        String email,
        Long userId,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
