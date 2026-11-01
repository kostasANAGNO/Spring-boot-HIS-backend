package com.kostasanagno.his.service;

/**
 * Published (inside the discharge transaction) when a patient has been discharged, so other modules can close what
 * only makes sense while the patient is admitted (e.g. open medication orders) without the hospitalization module
 * knowing about them.
 */
public record HospitalizationDischargedEvent(Long hospitalizationId) {
}
