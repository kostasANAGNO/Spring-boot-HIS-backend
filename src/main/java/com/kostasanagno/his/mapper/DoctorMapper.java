package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.ClinicSummary;
import com.kostasanagno.his.dto.DoctorRequest;
import com.kostasanagno.his.dto.DoctorResponse;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Doctor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class, uses = SpecialtyMapper.class)
public interface DoctorMapper {

    /** Specialty and user links are resolved and set by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialty", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Doctor toEntity(DoctorRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialty", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "clinic", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(DoctorRequest request, @MappingTarget Doctor doctor);

    @Mapping(target = "userId", source = "user.id")
    DoctorResponse toResponse(Doctor doctor);

    ClinicSummary toClinicSummary(Clinic clinic);
}
