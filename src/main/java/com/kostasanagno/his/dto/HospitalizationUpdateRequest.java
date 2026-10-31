package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HospitalizationUpdateRequest(
        @NotNull Long attendingDoctorId,
        @NotBlank @Size(max = 500) String admissionReason,
        @Size(max = 2000) String diagnosis) {
}
