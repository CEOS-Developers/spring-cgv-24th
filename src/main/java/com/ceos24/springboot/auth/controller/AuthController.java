package com.ceos24.springboot.auth.controller;

import com.ceos24.springboot.auth.dto.LoginRequest;
import com.ceos24.springboot.auth.dto.LoginResponse;
import com.ceos24.springboot.auth.dto.SignupRequest;
import com.ceos24.springboot.auth.dto.SignupResponse;
import com.ceos24.springboot.auth.service.AuthService;
import com.ceos24.springboot.auth.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signupService.signupUser(request));
    }
}