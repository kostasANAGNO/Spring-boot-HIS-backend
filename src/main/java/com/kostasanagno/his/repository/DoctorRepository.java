package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Doctor;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DoctorRepository extends JpaRepository<Doctor, Long>, JpaSpecificationExecutor<Doctor> {

    /** Fetches the specialty in the same query (no N+1 on the search endpoint). */
    @Override
    @EntityGraph(attributePaths = {"specialty", "clinic"})
    Page<Doctor> findAll(Specification<Doctor> spec, Pageable pageable);

    /** Soft-deleted doctors are never returned by these lookups. */
    @EntityGraph(attributePaths = {"specialty", "clinic"})
    Optional<Doctor> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = {"specialty", "clinic"})
    Optional<Doctor> findByUserUsernameIgnoreCaseAndActiveTrue(String username);

    /** The checks below look at all rows, soft-deleted included, because these values stay unique in the table. */
    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndIdNot(Long userId, Long id);
}
