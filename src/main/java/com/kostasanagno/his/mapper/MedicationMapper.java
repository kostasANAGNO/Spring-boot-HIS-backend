package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.MedicationRequest;
import com.kostasanagno.his.dto.MedicationResponse;
import com.kostasanagno.his.dto.MedicationSummary;
import com.kostasanagno.his.entity.Medication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface MedicationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Medication toEntity(MedicationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(MedicationRequest request, @MappingTarget Medication medication);

    MedicationResponse toResponse(Medication medication);

    MedicationSummary toSummary(Medication medication);
}
