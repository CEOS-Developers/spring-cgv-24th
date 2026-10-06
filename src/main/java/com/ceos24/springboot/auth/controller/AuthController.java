package com.ceos24.springboot.auth.controller;

import com.ceos24.springboot.auth.dto.LoginRequest;
import com.ceos24.springboot.auth.dto.LoginResponse;
import com.ceos24.springboot.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }
}