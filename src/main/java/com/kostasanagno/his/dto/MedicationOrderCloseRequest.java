package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason for rejecting, cancelling or stopping an order. */
public record MedicationOrderCloseRequest(@NotBlank @Size(max = 500) String reason) {
}
