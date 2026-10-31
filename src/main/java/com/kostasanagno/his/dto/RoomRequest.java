package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomRequest(@NotNull Long wardId, @NotBlank @Size(max = 20) String roomNumber) {
}
