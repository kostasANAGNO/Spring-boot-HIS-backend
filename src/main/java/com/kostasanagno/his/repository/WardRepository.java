package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Ward;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WardRepository extends JpaRepository<Ward, Long>, JpaSpecificationExecutor<Ward> {

    @Override
    @EntityGraph(attributePaths = "clinic")
    Optional<Ward> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "clinic")
    List<Ward> findAll(Specification<Ward> spec, Sort sort);

    /** Ward names are unique inside a clinic. */
    boolean existsByClinicIdAndNameIgnoreCase(Long clinicId, String name);

    boolean existsByClinicIdAndNameIgnoreCaseAndIdNot(Long clinicId, String name, Long id);
}
