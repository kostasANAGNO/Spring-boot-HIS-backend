package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.ChargeItemSearchCriteria;
import com.kostasanagno.his.entity.ChargeItem;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class ChargeItemSpecifications {

    private ChargeItemSpecifications() {
    }

    /** Active items unless {@code active=false}; {@code text} matches code or name. */
    public static Specification<ChargeItem> from(ChargeItemSearchCriteria criteria) {
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
}
