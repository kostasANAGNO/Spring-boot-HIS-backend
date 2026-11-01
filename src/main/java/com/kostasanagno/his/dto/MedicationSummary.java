package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.MedicationForm;

public record MedicationSummary(Long id, String code, String name, MedicationForm form, String strength,
        boolean controlled) {
}
