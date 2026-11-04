package com.example.archfixtures.controller;

import com.example.archfixtures.entity.BrokenEntity;
import com.example.archfixtures.repository.BrokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Violates: not under /api/, uses a repository, field injection, mutable state, @Transactional on a controller,
 * an endpoint without @PreAuthorize and @Audited, and one that leaks an entity.
 */
@RestController
@RequestMapping("/not-api")
@Transactional
public class WrongController {

    @Autowired
    private BrokenRepository repository;

    @Value("${some.property}")
    private String property;

    private int counter;

    @GetMapping("/open")
    public String open() {
        return "no role rule, no audit";
    }

    @PostMapping("/leak")
    @PreAuthorize("hasRole('ADMIN')")
    public BrokenEntity leak(BrokenEntity input) {
        return input;
    }
}
