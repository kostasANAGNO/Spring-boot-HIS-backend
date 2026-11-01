package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.DoctorSearchCriteria;
import com.kostasanagno.his.entity.Doctor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class DoctorSpecifications {

    private static final char ESCAPE = '\\';

    private DoctorSpecifications() {
    }

    /** Doctors matching every given criterion; active ones only unless {@code active=false} is requested. */
    public static Specification<Doctor> from(DoctorSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean active = criteria == null || criteria.active() == null || criteria.active();
            predicates.add(cb.equal(root.get("active"), active));

            if (criteria != null) {
                if (StringUtils.hasText(criteria.name())) {
                    for (String token : criteria.name().trim().split("\\s+")) {
                        predicates.add(cb.or(
                                contains(cb, root.get("firstName"), token),
                                contains(cb, root.get("lastName"), token)));
                    }
                }
                if (StringUtils.hasText(criteria.licenseNumber())) {
                    predicates.add(contains(cb, root.get("licenseNumber"), criteria.licenseNumber().trim()));
                }
                if (criteria.specialtyId() != null) {
                    predicates.add(cb.equal(root.get("specialty").get("id"), criteria.specialtyId()));
                }
                if (criteria.clinicId() != null) {
                    predicates.add(cb.equal(root.get("clinic").get("id"), criteria.clinicId()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<String> field, String term) {
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + escaped + "%", ESCAPE);
    }
}
