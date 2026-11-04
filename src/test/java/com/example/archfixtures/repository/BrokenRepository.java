package com.example.archfixtures.repository;

import com.example.archfixtures.service.impl.BrokenServiceImpl;

/** Violates: repositories do not know services. */
public class BrokenRepository {

    private BrokenServiceImpl service;
}
