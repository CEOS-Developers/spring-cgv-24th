package com.spring_cgv_24th.domain.member.entity;

import com.spring_cgv_24th.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Builder
    public Member(String email, String name) {
        this.email = email;
        this.name = name;
    }
}
