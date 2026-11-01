package com.kostasanagno.his.dto;

import com.kostasanagno.his.entity.AdmissionSource;
import com.kostasanagno.his.entity.AdmissionType;

/** The admission record. The initial diagnosis (code and text) is clinical and hidden from receptionists. */
public record AdmissionRecordResponse(
        AdmissionType admissionType,
        AdmissionSource admissionSource,
        String referringFacility,
        String reason,
        String initialDiagnosisCode,
        String initialDiagnosis) {

    public AdmissionRecordResponse withoutClinicalData() {
        return new AdmissionRecordResponse(admissionType, admissionSource, referringFacility, reason, null, null);
    }
}
