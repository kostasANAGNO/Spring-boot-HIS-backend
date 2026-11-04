package com.example.archfixtures.service;

import com.example.archfixtures.service.impl.BrokenServiceImpl;

/** Violates: a service interface must not know its implementation. */
public interface BrokenService {

    BrokenServiceImpl implementation();
}
