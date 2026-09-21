package com.ceos24.cgv.domain.user.entity;

import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_login_id",
                        columnNames = "login_id"
                )
        }
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "birthday", nullable = false)
    private LocalDate birthday;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    /**
     * 암호화된 비밀번호를 저장하므로 최대 길이를 255로 지정
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Builder
    private User(
            String nickname,
            LocalDate birthday,
            String loginId,
            String password,
            String phone
    ) {
        this.nickname = nickname;
        this.birthday = birthday;
        this.loginId = loginId;
        this.password = password;
        this.phone = phone;
    }

    public void updateProfile(
            String nickname,
            LocalDate birthday,
            String phone
    ) {
        this.nickname = nickname;
        this.birthday = birthday;
        this.phone = phone;
    }

    public void changePassword(String password) {
        this.password = password;
    }
}