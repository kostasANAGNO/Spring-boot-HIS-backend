package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.AuditEventSearchCriteria;
import com.kostasanagno.his.entity.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> from(AuditEventSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (StringUtils.hasText(criteria.username())) {
                    predicates.add(cb.equal(cb.lower(root.get("username")), criteria.username().trim().toLowerCase(Locale.ROOT)));
                }
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(root.get("patientId"), criteria.patientId()));
                }
                if (criteria.resourceType() != null) {
                    predicates.add(cb.equal(root.get("resourceType"), criteria.resourceType()));
                }
                if (criteria.resourceId() != null) {
                    predicates.add(cb.equal(root.get("resourceId"), criteria.resourceId()));
                }
                if (criteria.action() != null) {
                    predicates.add(cb.equal(root.get("action"), criteria.action()));
                }
                if (criteria.outcome() != null) {
                    predicates.add(cb.equal(root.get("outcome"), criteria.outcome()));
                }
                if (criteria.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), criteria.from()));
                }
                if (criteria.to() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), criteria.to()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
