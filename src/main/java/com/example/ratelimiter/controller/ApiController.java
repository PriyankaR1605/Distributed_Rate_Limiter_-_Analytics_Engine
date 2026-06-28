package com.example.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    @GetMapping("/data")
    public ResponseEntity<Map<String, String>> getData() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Data fetched successfully from protected resource."
        ));
    }

    @PostMapping("/profile")
    public ResponseEntity<Map<String, String>> updateProfile() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Profile updated successfully on rate-limited write action."
        ));
    }
}
