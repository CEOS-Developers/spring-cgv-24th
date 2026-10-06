package com.ceos24.cgv.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank(
                groups = {
                        ExistGroup.class,
                        AddGroup.class,
                        UpdateGroup.class,
                        DeleteGroup.class
                }
        )
        @Size(
                min = 4,
                groups = {
                        ExistGroup.class,
                        AddGroup.class,
                        UpdateGroup.class,
                        DeleteGroup.class
                }
        )
        String username,

        @NotBlank(
                groups = {
                        AddGroup.class,
                        PasswordGroup.class
                }
        )
        @Size(
                min = 4,
                groups = {
                        AddGroup.class,
                        PasswordGroup.class
                }
        )
        String password,

        @NotBlank(
                groups = {
                        AddGroup.class,
                        UpdateGroup.class
                }
        )
        String nickname
) {

        public interface ExistGroup { } // 회원 가입시 username 존재 확인

        public interface AddGroup { } // 회원 가입시

        public interface PasswordGroup { } // 비밀번호 변경시

        public interface UpdateGroup { } // 회원 수정시

        public interface DeleteGroup { } // 회원 삭제시
}
