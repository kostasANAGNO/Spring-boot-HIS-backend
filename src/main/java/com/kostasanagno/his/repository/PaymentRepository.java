package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = "recordedBy")
    List<Payment> findByInvoiceIdOrderByPaidAtAscIdAsc(Long invoiceId);
}
