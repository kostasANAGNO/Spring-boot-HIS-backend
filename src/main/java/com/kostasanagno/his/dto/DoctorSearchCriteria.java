package com.kostasanagno.his.dto;

/**
 * All filters are optional. {@code active} defaults to true when omitted, so soft-deleted doctors only
 * show up when explicitly asked for with {@code active=false}.
 */
public record DoctorSearchCriteria(
        String name,
        String licenseNumber,
        Long specialtyId,
        Long clinicId,
        Boolean active) {
}
