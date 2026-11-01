package com.kostasanagno.his.dto;

import java.time.Instant;

public record DispensingResponse(Long id, int quantity, String dispensedBy, Instant dispensedAt, String notes) {
}
