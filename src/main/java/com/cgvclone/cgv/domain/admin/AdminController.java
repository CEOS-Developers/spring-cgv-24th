package com.cgvclone.cgv.domain.admin;

import static org.springframework.http.HttpStatus.OK;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/check")
    public ResponseEntity<Void> check() {
        return ResponseEntity
                .status(OK)
                .build();
    }
}
