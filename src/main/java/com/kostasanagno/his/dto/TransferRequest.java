package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferRequest(@NotNull Long bedId, @Size(max = 255) String reason) {
}
