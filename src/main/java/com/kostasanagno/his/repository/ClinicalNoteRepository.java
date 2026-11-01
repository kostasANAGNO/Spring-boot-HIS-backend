package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.ClinicalNote;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, Long>, JpaSpecificationExecutor<ClinicalNote> {

    @Override
    @EntityGraph(attributePaths = {"patient", "authorDoctor", "authorDoctor.specialty", "appointment", "hospitalization"})
    Optional<ClinicalNote> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"patient", "authorDoctor", "authorDoctor.specialty", "appointment", "hospitalization"})
    Page<ClinicalNote> findAll(Specification<ClinicalNote> spec, Pageable pageable);
}
