package com.example.archfixtures.mapper;

import com.example.archfixtures.repository.BrokenRepository;

/** Violates: mappers only map, they do not reach into repositories. */
public class BrokenMappingHelper {

    private BrokenRepository repository;
}
