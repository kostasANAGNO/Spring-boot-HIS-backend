package com.example.archfixtures.mapper;

import org.mapstruct.Mapper;

/** Violates: mappers use CentralMapperConfig (so unmapped properties break the build). */
@Mapper
public interface BrokenMapper {
}
