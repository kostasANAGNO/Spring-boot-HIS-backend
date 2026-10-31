package com.kostasanagno.his.dto;

public record RoomResponse(Long id, String roomNumber, Long wardId, String wardName, boolean active) {
}
