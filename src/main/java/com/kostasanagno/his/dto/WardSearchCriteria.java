package com.kostasanagno.his.dto;

/** {@code active} defaults to true when omitted. */
public record WardSearchCriteria(Long clinicId, Boolean active) {
}
