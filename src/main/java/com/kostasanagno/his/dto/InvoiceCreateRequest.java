package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotNull;

/** Creates an empty DRAFT invoice; {@code appointmentId} (optional) must belong to the same patient. */
public record InvoiceCreateRequest(@NotNull Long patientId, Long appointmentId) {
}
