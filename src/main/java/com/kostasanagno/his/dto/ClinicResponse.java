package com.kostasanagno.his.dto;

public record ClinicResponse(Long id, String name, SpecialtyResponse specialty, boolean active) {
}
