package com.kostasanagno.his.dto;

import java.time.Instant;

public record ClinicalNoteVersionResponse(
        int version,
        String history,
        String examination,
        String assessment,
        String plan,
        String diagnosisCode,
        String changeReason,
        String editedBy,
        Instant createdAt) {
}
