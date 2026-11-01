package com.kostasanagno.his.dto;

/** {@code active} defaults to true when omitted. */
public record RoomSearchCriteria(Long clinicId, Long wardId, Boolean active) {
}
