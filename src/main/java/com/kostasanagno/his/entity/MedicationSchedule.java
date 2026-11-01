package com.kostasanagno.his.entity;

/** ONCE: a single dose. SCHEDULED: every N hours. AS_NEEDED: whenever needed, at least N hours apart. */
public enum MedicationSchedule {
    ONCE,
    SCHEDULED,
    AS_NEEDED
}
