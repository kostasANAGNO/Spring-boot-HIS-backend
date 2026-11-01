package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.MedicationForm;

public record MedicationResponse(Long id, String code, String name, MedicationForm form, String strength,
        String atcCode, boolean controlled, boolean active) {
}
