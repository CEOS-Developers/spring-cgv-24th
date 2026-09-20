package com.ceos.cgv.domain.user.controller;

import com.ceos.cgv.domain.user.dto.SignupRequest;
import com.ceos.cgv.domain.user.dto.SignupResponse;
import com.ceos.cgv.domain.user.service.RegistrationService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final RegistrationService registrationService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = SignupResponse.from(registrationService.register(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
