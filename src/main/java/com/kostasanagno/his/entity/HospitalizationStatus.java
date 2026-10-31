package com.kostasanagno.his.entity;

public enum HospitalizationStatus {
    ADMITTED,
    DISCHARGED;

    /** ADMITTED -> DISCHARGED; DISCHARGED is terminal. */
    public boolean canTransitionTo(HospitalizationStatus target) {
        return this == ADMITTED && target == DISCHARGED;
    }
}
