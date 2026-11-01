package com.kostasanagno.his.audit;

import com.kostasanagno.his.entity.AuditResource;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Finds which patient a record belongs to, for events that only know the record's id (void/sub-record endpoints). */
@Component
@RequiredArgsConstructor
public class PatientIdResolver {

    private final EntityManager entityManager;

    /** The owning patient's id, or null if the record does not exist or is not about a patient. */
    @Transactional(readOnly = true)
    public Long resolve(AuditResource resource, Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        String jpql = switch (resource) {
            case PATIENT -> null;
            case APPOINTMENT -> "select a.patient.id from Appointment a where a.id = :id";
            case HOSPITALIZATION -> "select h.patient.id from Hospitalization h where h.id = :id";
            case CLINICAL_NOTE -> "select n.patient.id from ClinicalNote n where n.id = :id";
            case INVOICE -> "select i.patient.id from Invoice i where i.id = :id";
            case AUDIT_LOG -> "";
        };
        if (resource == AuditResource.PATIENT) {
            return resourceId;
        }
        if (jpql == null || jpql.isEmpty()) {
            return null;
        }
        List<Long> result = entityManager.createQuery(jpql, Long.class).setParameter("id", resourceId).getResultList();
        return result.isEmpty() ? null : result.get(0);
    }
}
