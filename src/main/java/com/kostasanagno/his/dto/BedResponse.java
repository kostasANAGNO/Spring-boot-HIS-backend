package com.kostasanagno.his.dto;

public record BedResponse(
        Long id,
        String bedNumber,
        Long roomId,
        String roomNumber,
        Long wardId,
        String wardName,
        Long clinicId,
        String clinicName,
        boolean active,
        boolean occupied) {
}
