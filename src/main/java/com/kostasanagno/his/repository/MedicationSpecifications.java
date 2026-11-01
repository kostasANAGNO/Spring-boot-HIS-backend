package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.AdministrationSearchCriteria;
import com.kostasanagno.his.dto.MedicationOrderSearchCriteria;
import com.kostasanagno.his.dto.MedicationSearchCriteria;
import com.kostasanagno.his.entity.Medication;
import com.kostasanagno.his.entity.MedicationAdministration;
import com.kostasanagno.his.entity.MedicationOrder;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Search filters for the medication catalog, orders and administrations. */
public final class MedicationSpecifications {

    private MedicationSpecifications() {
    }

    /** Active medications unless {@code active=false}; {@code text} matches code or name. */
    public static Specification<Medication> catalog(MedicationSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            boolean active = criteria == null || criteria.active() == null || criteria.active();
            predicates.add(cb.equal(root.get("active"), active));
            if (criteria != null && StringUtils.hasText(criteria.text())) {
                String pattern = "%" + criteria.text().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<MedicationOrder> orders(MedicationOrderSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (criteria.hospitalizationId() != null) {
                    predicates.add(cb.equal(root.get("hospitalization").get("id"), criteria.hospitalizationId()));
                }
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(root.get("hospitalization").get("patient").get("id"), criteria.patientId()));
                }
                if (criteria.medicationId() != null) {
                    predicates.add(cb.equal(root.get("medication").get("id"), criteria.medicationId()));
                }
                if (criteria.prescribedByDoctorId() != null) {
                    predicates.add(cb.equal(root.get("prescribedBy").get("id"), criteria.prescribedByDoctorId()));
                }
                if (criteria.status() != null) {
                    predicates.add(cb.equal(root.get("status"), criteria.status()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<MedicationAdministration> administrations(AdministrationSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (criteria.orderId() != null) {
                    predicates.add(cb.equal(root.get("order").get("id"), criteria.orderId()));
                }
                if (criteria.hospitalizationId() != null) {
                    predicates.add(cb.equal(root.get("order").get("hospitalization").get("id"), criteria.hospitalizationId()));
                }
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(
                            root.get("order").get("hospitalization").get("patient").get("id"), criteria.patientId()));
                }
                if (criteria.outcome() != null) {
                    predicates.add(cb.equal(root.get("outcome"), criteria.outcome()));
                }
                if (criteria.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("administeredAt"), criteria.from()));
                }
                if (criteria.to() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("administeredAt"), criteria.to()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
