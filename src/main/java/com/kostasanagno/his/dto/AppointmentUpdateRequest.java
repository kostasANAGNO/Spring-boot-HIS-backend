package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Reschedule/update of a SCHEDULED appointment. The patient cannot be changed. */
public record AppointmentUpdateRequest(
        @NotNull Long doctorId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotBlank @Size(max = 255) String reason,
        @Size(max = 2000) String notes) {
}
