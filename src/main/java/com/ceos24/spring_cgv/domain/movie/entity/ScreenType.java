package com.ceos24.spring_cgv.domain.movie.entity;

import com.ceos24.spring_cgv.domain.movie.enums.ScreenCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "screen_type",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_screen_type_name",
                columnNames = {"name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreenType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 30, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 10, nullable = false)
    private ScreenCategory category;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "col_count", nullable = false)
    private Integer colCount;

    @Column(name = "extra_charge", nullable = false)
    private Integer extraCharge;

    @Builder
    private ScreenType(String name, ScreenCategory category, Integer rowCount, Integer colCount, Integer extraCharge) {
        this.name = name;
        this.category = category;
        this.rowCount = rowCount;
        this.colCount = colCount;
        this.extraCharge = extraCharge == null ? 0 : extraCharge;
    }
}