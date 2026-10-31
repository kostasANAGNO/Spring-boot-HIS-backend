package com.kostasanagno.his.entity;

public enum AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** SCHEDULED -> COMPLETED | CANCELLED | NO_SHOW; the other statuses are terminal. */
    public boolean canTransitionTo(AppointmentStatus target) {
        return this == SCHEDULED && target != SCHEDULED;
    }
}
