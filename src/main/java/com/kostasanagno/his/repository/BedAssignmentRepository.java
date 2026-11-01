package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.BedAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BedAssignmentRepository extends JpaRepository<BedAssignment, Long> {

    String BED_GRAPH = "bed";

    @EntityGraph(attributePaths = {"bed", "bed.room", "bed.room.ward", "bed.room.ward.clinic"})
    Optional<BedAssignment> findByHospitalizationIdAndEndTimeIsNull(Long hospitalizationId);

    @EntityGraph(attributePaths = {"bed", "bed.room", "bed.room.ward", "bed.room.ward.clinic"})
    List<BedAssignment> findByHospitalizationIdInAndEndTimeIsNull(Collection<Long> hospitalizationIds);

    /** Whole movement history of one hospitalization, oldest first. */
    @EntityGraph(attributePaths = {"bed", "bed.room", "bed.room.ward", "bed.room.ward.clinic"})
    List<BedAssignment> findByHospitalizationIdOrderByStartTimeAsc(Long hospitalizationId);

    /** Is the bed taken at any moment from {@code from} onwards (an open assignment or one ending after it)? */
    @Query("select count(a) > 0 from BedAssignment a where a.bed.id = :bedId "
            + "and (a.endTime is null or a.endTime > :from)")
    boolean isBedTakenFrom(@Param("bedId") Long bedId, @Param("from") Instant from);

    /** Ids (out of {@code bedIds}) of beds that currently have a patient in them. */
    @Query("select distinct a.bed.id from BedAssignment a where a.bed.id in :bedIds and a.endTime is null")
    List<Long> findOccupiedBedIds(@Param("bedIds") Collection<Long> bedIds);

    boolean existsByBedIdAndEndTimeIsNull(Long bedId);

    boolean existsByBedRoomIdAndEndTimeIsNull(Long roomId);

    boolean existsByBedRoomWardIdAndEndTimeIsNull(Long wardId);

    boolean existsByBedRoomWardClinicIdAndEndTimeIsNull(Long clinicId);
}
