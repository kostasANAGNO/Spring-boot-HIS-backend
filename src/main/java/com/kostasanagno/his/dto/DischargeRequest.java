package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code dischargeTime} defaults to now; it must be after the last bed assignment started and not in the future. */
public record DischargeRequest(@NotBlank @Size(max = 2000) String dischargeNotes, Instant dischargeTime) {
}
