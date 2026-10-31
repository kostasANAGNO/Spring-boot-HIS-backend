package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BedRequest(@NotNull Long roomId, @NotBlank @Size(max = 20) String bedNumber) {
}
