package com.kostasanagno.his.entity;

public enum MedicationOrderStatus {
    PENDING,
    DISPENSED,
    REJECTED,
    CANCELLED,
    STOPPED,
    COMPLETED;

    /**
     * PENDING -> DISPENSED | REJECTED | CANCELLED; DISPENSED -> COMPLETED | STOPPED; the rest are final.
     * (A repeated dispensing keeps the order DISPENSED.)
     */
    public boolean canTransitionTo(MedicationOrderStatus target) {
        return switch (this) {
            case PENDING -> target == DISPENSED || target == REJECTED || target == CANCELLED;
            case DISPENSED -> target == COMPLETED || target == STOPPED;
            case REJECTED, CANCELLED, STOPPED, COMPLETED -> false;
        };
    }
}
