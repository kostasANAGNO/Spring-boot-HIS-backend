package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.BedRequest;
import com.kostasanagno.his.dto.BedResponse;
import com.kostasanagno.his.dto.BedSummary;
import com.kostasanagno.his.entity.Bed;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface BedMapper {

    /** The room is resolved and set by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Bed toEntity(BedRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(BedRequest request, @MappingTarget Bed bed);

    /** Whether the bed is occupied is computed by the service (one query per page, not per bed). */
    @Mapping(target = "id", source = "bed.id")
    @Mapping(target = "active", source = "bed.active")
    @Mapping(target = "roomId", source = "bed.room.id")
    @Mapping(target = "roomNumber", source = "bed.room.roomNumber")
    @Mapping(target = "wardId", source = "bed.room.ward.id")
    @Mapping(target = "wardName", source = "bed.room.ward.name")
    @Mapping(target = "occupied", source = "occupied")
    BedResponse toResponse(Bed bed, boolean occupied);

    @Mapping(target = "roomNumber", source = "room.roomNumber")
    @Mapping(target = "wardName", source = "room.ward.name")
    BedSummary toSummary(Bed bed);
}
