package com.Daad.ecommerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public String healthCheck() {
        return "Backend is working!";
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}


