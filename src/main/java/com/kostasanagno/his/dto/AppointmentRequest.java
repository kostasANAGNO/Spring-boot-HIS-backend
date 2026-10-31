package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Booking request. Times are ISO-8601 with offset, e.g. {@code 2026-11-02T09:30:00+02:00}. */
public record AppointmentRequest(
        @NotNull Long patientId,
        @NotNull Long doctorId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 2000) String notes) {
}
