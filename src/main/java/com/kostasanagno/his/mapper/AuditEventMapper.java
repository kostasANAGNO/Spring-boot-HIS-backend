package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.AuditEventResponse;
import com.kostasanagno.his.entity.AuditEvent;
import org.mapstruct.Mapper;

@Mapper(config = CentralMapperConfig.class)
public interface AuditEventMapper {

    AuditEventResponse toResponse(AuditEvent event);
}
