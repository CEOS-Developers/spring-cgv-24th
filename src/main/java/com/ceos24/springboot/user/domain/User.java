package com.ceos24.springboot.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "cgv_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

//  사용자 로그인 아이디로 email을 사용(중복 막음)
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "phone", length = 20)
    private String phone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    private User(
            String name,
            String email,
            String password,
            String phone
    ) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.role = Role.USER; // 자동으로 회원으로 지정
    }

    public static User create(
            String name,
            String email,
            String password,
            String phone
    ) {
        return new User(name, email, password, phone);
    }
}