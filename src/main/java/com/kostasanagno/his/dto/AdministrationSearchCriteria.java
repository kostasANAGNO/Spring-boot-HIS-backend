package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AdministrationOutcome;
import java.time.Instant;

/** All filters are optional; {@code from}/{@code to} bound the time of administration (inclusive). */
public record AdministrationSearchCriteria(
        Long hospitalizationId,
        Long orderId,
        Long patientId,
        AdministrationOutcome outcome,
        Instant from,
        Instant to) {
}
