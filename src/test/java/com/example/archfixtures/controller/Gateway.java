package com.example.archfixtures.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Violates: a @RestController is named *Controller. */
@RestController
@RequestMapping("/api/gateway")
public class Gateway {
}
