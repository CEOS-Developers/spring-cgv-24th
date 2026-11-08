package com.ceos24.springboot.admin.controller;

import com.ceos24.springboot.auth.dto.SignupRequest;
import com.ceos24.springboot.auth.dto.SignupResponse;
import com.ceos24.springboot.auth.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final SignupService signupService;

    @GetMapping("/check")
    public ResponseEntity<String> checkAdmin() {
        return ResponseEntity.ok("관리자 접근 성공");

    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signupAdmin(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(signupService.signupAdmin(request));
    }
}