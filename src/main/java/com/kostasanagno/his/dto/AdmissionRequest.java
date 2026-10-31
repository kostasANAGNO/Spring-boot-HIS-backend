package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code admissionTime} defaults to now and must not be in the future. */
public record AdmissionRequest(
        @NotNull Long patientId,
        @NotNull Long attendingDoctorId,
        @NotNull Long bedId,
        @NotBlank @Size(max = 500) String admissionReason,
        @Size(max = 2000) String diagnosis,
        Instant admissionTime,
        Long appointmentId) {
}
