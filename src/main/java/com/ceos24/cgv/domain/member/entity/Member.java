package com.ceos24.cgv.domain.member.entity;

import com.ceos24.cgv.domain.member.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member")
@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    private String loginId;

    private String password;

    @Enumerated(EnumType.STRING)
    Role role;
}
