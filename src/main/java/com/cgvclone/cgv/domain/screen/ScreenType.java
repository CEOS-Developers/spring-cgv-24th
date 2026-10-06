package com.cgvclone.cgv.domain.screen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "screen_types", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_screen_type_name",
                columnNames = {"name"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreenType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long screenTypeId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer rowCount;

    @Column(nullable = false)
    private Integer columnCount;
}
