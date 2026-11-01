package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.ClinicRequest;
import com.kostasanagno.his.dto.ClinicResponse;
import com.kostasanagno.his.dto.SpecialtyResponse;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Specialty;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface ClinicMapper {

    /** The specialty (optional) is resolved and set by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialty", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Clinic toEntity(ClinicRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialty", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ClinicRequest request, @MappingTarget Clinic clinic);

    ClinicResponse toResponse(Clinic clinic);

    SpecialtyResponse toSpecialtyResponse(Specialty specialty);
}
