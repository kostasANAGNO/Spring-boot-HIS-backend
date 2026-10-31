package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.AppointmentSearchCriteria;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.AppointmentStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    /** Appointments matching every given criterion; from/to select those overlapping the window. */
    public static Specification<Appointment> from(AppointmentSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (criteria.doctorId() != null) {
                    predicates.add(cb.equal(root.get("doctor").get("id"), criteria.doctorId()));
                }
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(root.get("patient").get("id"), criteria.patientId()));
                }
                if (criteria.status() != null) {
                    predicates.add(cb.equal(root.get("status"), criteria.status()));
                }
                if (criteria.to() != null) {
                    predicates.add(cb.lessThan(root.get("startTime"), criteria.to()));
                }
                if (criteria.from() != null) {
                    predicates.add(cb.greaterThan(root.get("endTime"), criteria.from()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** SCHEDULED appointments of this doctor overlapping [start, end) (half-open), ignoring {@code excludeId}. */
    public static Specification<Appointment> doctorBooked(Long doctorId, Instant start, Instant end, Long excludeId) {
        return scheduledOverlap("doctor", doctorId, start, end, excludeId);
    }

    /** SCHEDULED appointments of this patient overlapping [start, end) (half-open), ignoring {@code excludeId}. */
    public static Specification<Appointment> patientBooked(Long patientId, Instant start, Instant end, Long excludeId) {
        return scheduledOverlap("patient", patientId, start, end, excludeId);
    }

    private static Specification<Appointment> scheduledOverlap(String participant, Long participantId,
            Instant start, Instant end, Long excludeId) {
        return (root, query, cb) -> {
            var predicate = cb.and(
                    cb.equal(root.get(participant).get("id"), participantId),
                    cb.equal(root.get("status"), AppointmentStatus.SCHEDULED),
                    cb.lessThan(root.get("startTime"), end),
                    cb.greaterThan(root.get("endTime"), start));
            return excludeId == null ? predicate : cb.and(predicate, cb.notEqual(root.get("id"), excludeId));
        };
    }
}
