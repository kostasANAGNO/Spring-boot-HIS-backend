package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Clinic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    @Override
    @EntityGraph(attributePaths = "specialty")
    Optional<Clinic> findById(Long id);

    @EntityGraph(attributePaths = "specialty")
    List<Clinic> findAllByActiveOrderByNameAsc(boolean active);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
