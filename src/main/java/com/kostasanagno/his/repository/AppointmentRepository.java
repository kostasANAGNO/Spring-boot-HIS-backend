package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Appointment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /** Patient, doctor and specialty are fetched in the same query (responses show all three). */
    @Override
    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.specialty"})
    Optional<Appointment> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.specialty"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);
}
