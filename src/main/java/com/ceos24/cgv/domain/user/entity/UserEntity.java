package com.ceos24.cgv.domain.user.entity;

import com.ceos24.cgv.domain.user.dto.request.UserRequest;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false, unique = true, updatable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String nickname;

    @Column(name = "is_lock", nullable = false)
    private Boolean isLock;

    @Column(name = "is_social", nullable = false)
    private Boolean isSocial;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider_type")
    private SocialProviderType socialProviderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role_type",nullable = false)
    private UserRoleType roleType;


    public static UserEntity createLocalUser(
            String username,
            String encodedPassword
    ) {
        UserEntity userEntity = new UserEntity();
        userEntity.username = username;
        userEntity.password = encodedPassword;
        userEntity.isLock = false;
        userEntity.isSocial = false;
        userEntity.socialProviderType = null;
        userEntity.roleType = UserRoleType.USER;
        return userEntity;
    }

    public void updateUser(UserRequest request) {
        this.nickname = request.nickname();
    }
}
