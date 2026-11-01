package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.AdmissionRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionRecordRepository extends JpaRepository<AdmissionRecord, Long> {

    Optional<AdmissionRecord> findByHospitalizationId(Long hospitalizationId);

    /** The records of a whole page of hospitalizations in one query. */
    List<AdmissionRecord> findByHospitalizationIdIn(Collection<Long> hospitalizationIds);
}
