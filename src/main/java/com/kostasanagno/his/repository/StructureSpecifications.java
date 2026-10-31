package com.kostasanagno.his.repository;

import com.kostasanagno.his.dto.BedSearchCriteria;
import com.kostasanagno.his.dto.RoomSearchCriteria;
import com.kostasanagno.his.entity.Bed;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Room;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Search filters for rooms and beds. */
public final class StructureSpecifications {

    private StructureSpecifications() {
    }

    /** Rooms of the ward (optional); active ones unless {@code active=false} is requested. */
    public static Specification<Room> rooms(RoomSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), isActive(criteria == null ? null : criteria.active())));
            if (criteria != null && criteria.wardId() != null) {
                predicates.add(cb.equal(root.get("ward").get("id"), criteria.wardId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Beds filtered by ward/room/availability; active ones unless {@code active=false} is requested. */
    public static Specification<Bed> beds(BedSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("active"), isActive(criteria == null ? null : criteria.active())));
            if (criteria != null) {
                if (criteria.roomId() != null) {
                    predicates.add(cb.equal(root.get("room").get("id"), criteria.roomId()));
                }
                if (criteria.wardId() != null) {
                    predicates.add(cb.equal(root.get("room").get("ward").get("id"), criteria.wardId()));
                }
                if (criteria.available() != null) {
                    var open = query.subquery(Long.class);
                    var assignment = open.from(BedAssignment.class);
                    open.select(assignment.get("id")).where(
                            cb.equal(assignment.get("bed"), root),
                            cb.isNull(assignment.get("endTime")));
                    predicates.add(criteria.available() ? cb.not(cb.exists(open)) : cb.exists(open));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean isActive(Boolean active) {
        return active == null || active;
    }
}
