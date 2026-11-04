package com.example.archfixtures.exception;

import com.example.archfixtures.service.impl.BrokenServiceImpl;

/** Violates: exceptions do not know services. */
public class BrokenException extends RuntimeException {

    private BrokenServiceImpl service;
}
