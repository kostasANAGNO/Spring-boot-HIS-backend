package com.kostasanagno.his.audit;

/**
 * Implemented by response records that identify an audited record and the patient it concerns, so the audit
 * aspect can log exactly which records a response disclosed.
 */
public interface Auditable {

    Long auditResourceId();

    /** The patient this record is about, or null if it is not about a patient. */
    Long auditPatientId();
}
