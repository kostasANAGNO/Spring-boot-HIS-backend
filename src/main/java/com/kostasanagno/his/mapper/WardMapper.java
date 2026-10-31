package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.WardRequest;
import com.kostasanagno.his.dto.WardResponse;
import com.kostasanagno.his.entity.Ward;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface WardMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Ward toEntity(WardRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(WardRequest request, @MappingTarget Ward ward);

    WardResponse toResponse(Ward ward);
}
