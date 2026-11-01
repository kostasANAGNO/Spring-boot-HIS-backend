package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.ChargeItemRequest;
import com.kostasanagno.his.dto.ChargeItemResponse;
import com.kostasanagno.his.entity.ChargeItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface ChargeItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ChargeItem toEntity(ChargeItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ChargeItemRequest request, @MappingTarget ChargeItem chargeItem);

    ChargeItemResponse toResponse(ChargeItem chargeItem);
}
