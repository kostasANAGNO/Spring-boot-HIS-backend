package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInvoiceRequest(@NotBlank @Size(max = 500) String reason) {
}
