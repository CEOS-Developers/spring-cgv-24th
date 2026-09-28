package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.domain.user.dto.request.UserRequest;
import com.ceos24.cgv.domain.user.service.UserService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 자체 로그인 유저 존재 확인
    @PostMapping("/user/exist")
    public ResponseEntity<ApiResponse<Boolean>> existUserApi(
            @Validated(UserRequest.ExistGroup.class)
            @RequestBody UserRequest request
    ) {
        boolean exists = userService.existUser(request);

        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<Boolean> body = new ApiResponse<>(
                exists,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 회원가입
    @PostMapping("/user")
    public ResponseEntity<ApiResponse<Long>> joinApi(
            @Validated(UserRequest.AddGroup.class)
            @RequestBody UserRequest request
    ) {
        Long id = userService.addUser(request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<Long> body = new ApiResponse<>(
                id,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
