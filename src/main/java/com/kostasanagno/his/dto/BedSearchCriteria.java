package com.kostasanagno.his.dto;

/**
 * {@code available=true} keeps beds without an open occupancy, {@code available=false} the occupied ones.
 * {@code active} defaults to true when omitted.
 */
public record BedSearchCriteria(Long wardId, Long roomId, Boolean available, Boolean active) {
}
