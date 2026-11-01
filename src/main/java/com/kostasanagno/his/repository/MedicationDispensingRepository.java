package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.MedicationDispensing;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationDispensingRepository extends JpaRepository<MedicationDispensing, Long> {

    @EntityGraph(attributePaths = "dispensedBy")
    List<MedicationDispensing> findByOrderIdOrderByDispensedAtAscIdAsc(Long orderId);
}
