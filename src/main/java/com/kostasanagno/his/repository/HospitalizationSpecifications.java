package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.HospitalizationSearchCriteria;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Hospitalization;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class HospitalizationSpecifications {

    private HospitalizationSpecifications() {
    }

    public static Specification<Hospitalization> from(HospitalizationSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(root.get("patient").get("id"), criteria.patientId()));
                }
                if (criteria.doctorId() != null) {
                    predicates.add(cb.equal(root.get("attendingDoctor").get("id"), criteria.doctorId()));
                }
                if (criteria.status() != null) {
                    predicates.add(cb.equal(root.get("status"), criteria.status()));
                }
                if (criteria.admittedFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("admissionTime"), criteria.admittedFrom()));
                }
                if (criteria.admittedTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("admissionTime"), criteria.admittedTo()));
                }
                if (criteria.bedId() != null || criteria.wardId() != null) {
                    predicates.add(cb.exists(lastBedMatches(criteria, root, query, cb)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Subquery: the hospitalization's latest bed assignment (current bed, or last bed once discharged) matches. */
    private static jakarta.persistence.criteria.Subquery<Long> lastBedMatches(HospitalizationSearchCriteria criteria,
            jakarta.persistence.criteria.Root<Hospitalization> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query, jakarta.persistence.criteria.CriteriaBuilder cb) {
        var match = query.subquery(Long.class);
        var assignment = match.from(BedAssignment.class);

        var latest = match.subquery(Instant.class);
        var other = latest.from(BedAssignment.class);
        latest.select(cb.greatest(other.<Instant>get("startTime")))
                .where(cb.equal(other.get("hospitalization"), root));

        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(assignment.get("hospitalization"), root));
        conditions.add(cb.equal(assignment.<Instant>get("startTime"), latest));
        if (criteria.bedId() != null) {
            conditions.add(cb.equal(assignment.get("bed").get("id"), criteria.bedId()));
        }
        if (criteria.wardId() != null) {
            conditions.add(cb.equal(assignment.get("bed").get("room").get("ward").get("id"), criteria.wardId()));
        }
        return match.select(assignment.get("id")).where(conditions.toArray(Predicate[]::new));
    }
}
