package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.AppointmentStatus;
import java.time.Instant;

public record AppointmentResponse(
        Long id,
        PatientSummary patient,
        DoctorSummary doctor,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status,
        String reason,
        String notes,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) implements Auditable {

    @Override
    public Long auditResourceId() {
        return id;
    }

    @Override
    public Long auditPatientId() {
        return patient.id();
    }

}
