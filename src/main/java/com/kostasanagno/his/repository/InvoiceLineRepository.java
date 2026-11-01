package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.InvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    @EntityGraph(attributePaths = "chargeItem")
    List<InvoiceLine> findByInvoiceIdOrderByIdAsc(Long invoiceId);
}
