package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.MedicationOrder;
import com.kostasanagno.his.entity.MedicationOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationOrderRepository
        extends JpaRepository<MedicationOrder, Long>, JpaSpecificationExecutor<MedicationOrder> {

    String[] GRAPH = {"hospitalization", "hospitalization.patient", "medication", "prescribedBy",
            "prescribedBy.specialty"};

    @Override
    @EntityGraph(attributePaths = {"hospitalization", "hospitalization.patient", "medication", "prescribedBy",
            "prescribedBy.specialty"})
    Optional<MedicationOrder> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"hospitalization", "hospitalization.patient", "medication", "prescribedBy",
            "prescribedBy.specialty"})
    Page<MedicationOrder> findAll(Specification<MedicationOrder> spec, Pageable pageable);

    /**
     * Loads the order with a row lock so concurrent changes (two nurses recording the same dose, a dispensing racing a
     * discharge) run one after the other. Used by every operation that changes an order or adds to it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MedicationOrder o where o.id = :id")
    Optional<MedicationOrder> findByIdForUpdate(@Param("id") Long id);

    /** Ids of the orders of a hospitalization that are still open (to close them when the patient leaves). */
    @Query("select o.id from MedicationOrder o where o.hospitalization.id = :hospitalizationId and o.status in :statuses")
    List<Long> findIdsByHospitalizationAndStatusIn(@Param("hospitalizationId") Long hospitalizationId,
            @Param("statuses") Collection<MedicationOrderStatus> statuses);
}
