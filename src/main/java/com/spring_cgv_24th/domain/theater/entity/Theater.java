package com.spring_cgv_24th.domain.theater.entity;

import com.spring_cgv_24th.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "theater")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Theater extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "theater_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Builder
    public Theater(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
