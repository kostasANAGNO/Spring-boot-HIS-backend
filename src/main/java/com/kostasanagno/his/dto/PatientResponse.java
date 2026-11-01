package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.Gender;
import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Gender gender,
        String nationalId,
        String phone,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        boolean active,
        Instant createdAt,
        Instant updatedAt) implements Auditable {

    @Override
    public Long auditResourceId() {
        return id;
    }

    @Override
    public Long auditPatientId() {
        return id;
    }

}
