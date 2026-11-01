package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.Room;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    @Override
    @EntityGraph(attributePaths = {"ward", "ward.clinic"})
    Optional<Room> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"ward", "ward.clinic"})
    Page<Room> findAll(Specification<Room> spec, Pageable pageable);

    boolean existsByWardIdAndRoomNumberIgnoreCase(Long wardId, String roomNumber);

    boolean existsByWardIdAndRoomNumberIgnoreCaseAndIdNot(Long wardId, String roomNumber, Long id);
}
