package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.DischargeType;
import java.time.LocalDate;

/**
 * The discharge record. The final diagnosis, the summary and the instructions are clinical and hidden from
 * receptionists; the type, destination and follow-up date stay visible for administrative work.
 */
public record DischargeRecordResponse(
        DischargeType dischargeType,
        String destinationFacility,
        String finalDiagnosisCode,
        String finalDiagnosis,
        String summary,
        String instructions,
        LocalDate followUpDate) {

    public DischargeRecordResponse withoutClinicalData() {
        return new DischargeRecordResponse(dischargeType, destinationFacility, null, null, null, null, followUpDate);
    }
}
