package com.ceos24.cgv.domain.user.entity;

import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 100)
    private String email;

    // 하이픈 없이 숫자만 저장한다. 표시 형식은 클라이언트가 정한다.
    @Column(nullable = false, length = 11)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // role을 빌더 파라미터로 열지 않는다. 가입 경로가 어디든 ADMIN이 섞여 들어올 틈을 없앤다.
    @Builder
    private User(String loginId, String password, String name, LocalDate birthDate,
                 String email, String phoneNumber) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.role = Role.USER;
    }

    // 관리자는 가입 API가 아니라 서버 초기화로만 만든다. 빌더에 role을 열지 않고 이름으로 의도를 드러낸다.
    public static User createAdmin(String loginId, String password, String name, LocalDate birthDate,
                                   String email, String phoneNumber) {
        User admin = User.builder()
                .loginId(loginId)
                .password(password)
                .name(name)
                .birthDate(birthDate)
                .email(email)
                .phoneNumber(phoneNumber)
                .build();
        admin.role = Role.ADMIN;
        return admin;
    }
}
