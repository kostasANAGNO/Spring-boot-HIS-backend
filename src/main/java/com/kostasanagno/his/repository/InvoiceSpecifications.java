package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.InvoiceSearchCriteria;
import com.kostasanagno.his.entity.Invoice;
import com.kostasanagno.his.entity.InvoiceStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    public static Specification<Invoice> from(InvoiceSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                if (criteria.patientId() != null) {
                    predicates.add(cb.equal(root.get("patient").get("id"), criteria.patientId()));
                }
                if (criteria.status() != null) {
                    predicates.add(cb.equal(root.get("status"), criteria.status()));
                }
                if (StringUtils.hasText(criteria.invoiceNumber())) {
                    String pattern = "%" + criteria.invoiceNumber().trim().toLowerCase(Locale.ROOT)
                            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                    predicates.add(cb.like(cb.lower(root.get("invoiceNumber")), pattern, '\\'));
                }
                if (Boolean.TRUE.equals(criteria.outstanding())) {
                    predicates.add(cb.equal(root.get("status"), InvoiceStatus.ISSUED));
                }
                if (criteria.createdFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), criteria.createdFrom()));
                }
                if (criteria.createdTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), criteria.createdTo()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
