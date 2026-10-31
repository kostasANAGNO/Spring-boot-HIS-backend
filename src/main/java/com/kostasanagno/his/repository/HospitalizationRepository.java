package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.HospitalizationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HospitalizationRepository
        extends JpaRepository<Hospitalization, Long>, JpaSpecificationExecutor<Hospitalization> {

    @Override
    @EntityGraph(attributePaths = {"patient", "attendingDoctor", "attendingDoctor.specialty", "appointment"})
    Optional<Hospitalization> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"patient", "attendingDoctor", "attendingDoctor.specialty"})
    Page<Hospitalization> findAll(Specification<Hospitalization> spec, Pageable pageable);

    boolean existsByPatientIdAndStatus(Long patientId, HospitalizationStatus status);

    boolean existsByAppointmentId(Long appointmentId);
}
