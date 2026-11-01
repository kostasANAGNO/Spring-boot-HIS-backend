package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Bed;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BedRepository extends JpaRepository<Bed, Long>, JpaSpecificationExecutor<Bed> {

    @Override
    @EntityGraph(attributePaths = {"room", "room.ward", "room.ward.clinic"})
    Optional<Bed> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"room", "room.ward", "room.ward.clinic"})
    Page<Bed> findAll(Specification<Bed> spec, Pageable pageable);

    boolean existsByRoomIdAndBedNumberIgnoreCase(Long roomId, String bedNumber);

    boolean existsByRoomIdAndBedNumberIgnoreCaseAndIdNot(Long roomId, String bedNumber, Long id);
}
