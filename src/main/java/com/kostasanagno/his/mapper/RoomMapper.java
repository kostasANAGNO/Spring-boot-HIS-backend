package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.RoomRequest;
import com.kostasanagno.his.dto.RoomResponse;
import com.kostasanagno.his.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface RoomMapper {

    /** The ward is resolved and set by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ward", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Room toEntity(RoomRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ward", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(RoomRequest request, @MappingTarget Room room);

    @Mapping(target = "wardId", source = "ward.id")
    @Mapping(target = "wardName", source = "ward.name")
    RoomResponse toResponse(Room room);
}
