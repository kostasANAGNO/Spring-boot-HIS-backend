package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.ClinicalNoteSearchCriteria;
import com.kostasanagno.his.entity.ClinicalNote;
import com.kostasanagno.his.entity.ClinicalNoteVersion;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class ClinicalNoteSpecifications {

    private ClinicalNoteSpecifications() {
    }

    public static Specification<ClinicalNote> from(ClinicalNoteSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(root.get("patient").get("id"), criteria.patientId()));
                }
                if (criteria.authorDoctorId() != null) {
                    predicates.add(cb.equal(root.get("authorDoctor").get("id"), criteria.authorDoctorId()));
                }
                if (criteria.appointmentId() != null) {
                    predicates.add(cb.equal(root.get("appointment").get("id"), criteria.appointmentId()));
                }
                if (criteria.hospitalizationId() != null) {
                    predicates.add(cb.equal(root.get("hospitalization").get("id"), criteria.hospitalizationId()));
                }
                if (criteria.createdFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), criteria.createdFrom()));
                }
                if (criteria.createdTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), criteria.createdTo()));
                }
                if (StringUtils.hasText(criteria.diagnosisCode())) {
                    // the CURRENT (highest numbered) version's code starts with the given prefix
                    var match = query.subquery(Long.class);
                    var version = match.from(ClinicalNoteVersion.class);
                    var latest = match.subquery(Integer.class);
                    var other = latest.from(ClinicalNoteVersion.class);
                    latest.select(cb.max(other.<Integer>get("versionNumber")))
                            .where(cb.equal(other.get("note"), root));
                    String prefix = criteria.diagnosisCode().trim().toLowerCase(Locale.ROOT)
                            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                    match.select(version.get("id")).where(
                            cb.equal(version.get("note"), root),
                            cb.equal(version.<Integer>get("versionNumber"), latest),
                            cb.like(cb.lower(version.get("diagnosisCode")), prefix + "%", '\\'));
                    predicates.add(cb.exists(match));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
