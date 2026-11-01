package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Invoice;
import com.kostasanagno.his.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
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

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    @Override
    @EntityGraph(attributePaths = {"patient", "appointment", "hospitalization"})
    Optional<Invoice> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"patient", "appointment", "hospitalization"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    /**
     * Loads the invoice with a row lock so concurrent changes (two payments, a payment racing an issue/cancel)
     * run one after the other. Used by every operation that changes an invoice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    /** A visit or a stay is invoiced once; a cancelled invoice frees it again. */
    boolean existsByAppointmentIdAndStatusNot(Long appointmentId, InvoiceStatus status);

    boolean existsByHospitalizationIdAndStatusNot(Long hospitalizationId, InvoiceStatus status);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();
}
