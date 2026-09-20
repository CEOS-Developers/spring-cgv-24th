package com.ceos.cgv.domain.user.entity;

import com.ceos.cgv.domain.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
        name = "uk_users_login_id", columnNames = "login_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "login_id", length = 20)
    private String loginId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16,
            columnDefinition = "varchar(16) default 'USER'")
    private UserRole role = UserRole.USER;

    @Builder
    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public static User register(String loginId, String name, String email, String passwordHash) {
        User user = new User(name, email);
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        return user;
    }
}
