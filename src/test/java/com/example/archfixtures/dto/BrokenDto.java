package com.example.archfixtures.dto;

import com.example.archfixtures.service.impl.BrokenServiceImpl;

/** Violates: DTOs are records, DTOs do not know services. */
public class BrokenDto {

    private BrokenServiceImpl service;
}
