package com.kostasanagno.his.dto;

public record BedResponse(
        Long id,
        String bedNumber,
        Long roomId,
        String roomNumber,
        Long wardId,
        String wardName,
        boolean active,
        boolean occupied) {
}
