package com.kostasanagno.his.entity;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    CANCELLED;

    /** DRAFT -> ISSUED | CANCELLED; ISSUED -> PAID | CANCELLED; PAID and CANCELLED are final. */
    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case DRAFT -> target == ISSUED || target == CANCELLED;
            case ISSUED -> target == PAID || target == CANCELLED;
            case PAID, CANCELLED -> false;
        };
    }
}
