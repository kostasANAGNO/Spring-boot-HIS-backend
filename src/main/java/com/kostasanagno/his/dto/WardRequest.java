package com.kostasanagno.his.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WardRequest(@NotBlank @Size(max = 100) String name) {
}
