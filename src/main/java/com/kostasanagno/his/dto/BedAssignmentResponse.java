package com.kostasanagno.his.dto;

import java.time.Instant;

/** One entry of a hospitalization's bed history; {@code endTime} is null for the current bed. */
public record BedAssignmentResponse(Long id, BedSummary bed, Instant startTime, Instant endTime, String reason) {

    public BedAssignmentResponse withoutClinicalData() {
        return new BedAssignmentResponse(id, bed, startTime, endTime, null);
    }
}
