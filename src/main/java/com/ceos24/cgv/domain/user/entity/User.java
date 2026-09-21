package com.ceos24.cgv.domain.user.entity;

import com.ceos24.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    private String userProfileImageUrl;

    private User(String loginId, String password, String nickname, String userProfileImageUrl) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.userProfileImageUrl = userProfileImageUrl;
    }

    public static User create(String loginId, String password, String nickname, String userProfileImageUrl) {
        return new User(loginId, password, nickname, userProfileImageUrl);
    }
}
