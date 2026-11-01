package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.DischargeRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DischargeRecordRepository extends JpaRepository<DischargeRecord, Long> {

    Optional<DischargeRecord> findByHospitalizationId(Long hospitalizationId);

    /** The records of a whole page of hospitalizations in one query. */
    List<DischargeRecord> findByHospitalizationIdIn(Collection<Long> hospitalizationIds);
}
