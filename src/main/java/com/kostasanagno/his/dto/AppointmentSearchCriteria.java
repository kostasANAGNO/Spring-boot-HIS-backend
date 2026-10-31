package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AppointmentStatus;
import java.time.Instant;

/**
 * All filters are optional. {@code from}/{@code to} select appointments that overlap that window
 * (ISO-8601 instants; encode a {@code +} offset as {@code %2B} in query strings).
 */
public record AppointmentSearchCriteria(
        Long doctorId,
        Long patientId,
        AppointmentStatus status,
        Instant from,
        Instant to) {

    public AppointmentSearchCriteria withDoctorId(Long newDoctorId) {
        return new AppointmentSearchCriteria(newDoctorId, patientId, status, from, to);
    }
}
