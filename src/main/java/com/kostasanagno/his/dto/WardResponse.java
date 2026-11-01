package com.kostasanagno.his.dto;

public record WardResponse(Long id, String name, Long clinicId, String clinicName, boolean active) {
}
