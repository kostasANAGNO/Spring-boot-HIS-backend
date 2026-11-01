package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.Role;
import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean enabled,
        Set<Role> roles,
        Instant createdAt) implements Auditable {

    @Override
    public Long auditResourceId() {
        return id;
    }

    /** A user account is not about a patient. */
    @Override
    public Long auditPatientId() {
        return null;
    }
}
