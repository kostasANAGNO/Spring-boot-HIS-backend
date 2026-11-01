package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.AdministrationOutcome;
import com.kostasanagno.his.entity.MedicationAdministration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicationAdministrationRepository
        extends JpaRepository<MedicationAdministration, Long>, JpaSpecificationExecutor<MedicationAdministration> {

    @Override
    @EntityGraph(attributePaths = {"order", "order.hospitalization", "administeredBy"})
    Page<MedicationAdministration> findAll(Specification<MedicationAdministration> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.hospitalization", "administeredBy"})
    List<MedicationAdministration> findByOrderIdOrderByAdministeredAtAscIdAsc(Long orderId);

    /** The latest dose actually given, to enforce the spacing between doses. */
    Optional<MedicationAdministration> findTopByOrderIdAndOutcomeOrderByAdministeredAtDesc(Long orderId,
            AdministrationOutcome outcome);
}
