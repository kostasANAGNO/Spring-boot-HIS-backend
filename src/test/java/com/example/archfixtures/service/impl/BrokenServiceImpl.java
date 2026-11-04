package com.example.archfixtures.service.impl;

import com.example.archfixtures.controller.WrongController;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * Violates: no @Transactional, no service interface, services do not know controllers, singleton beans are stateless,
 * no standard streams, no java.util.logging, no generic exceptions.
 */
@Service
public class BrokenServiceImpl {

    private static final Logger LOG = Logger.getLogger(BrokenServiceImpl.class.getName());

    private int counter;

    private final WrongController controller = null;

    public void doIt() {
        System.out.println("hello");
        LOG.info("y");
        counter++;
        throw new RuntimeException("boom");
    }
}
