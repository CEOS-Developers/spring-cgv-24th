package com.spring_cgv_24th.domain.auditorium.entity;

import com.spring_cgv_24th.domain.auditorium.enums.AuditoriumKind;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "auditorium_type",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"kind"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditoriumType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auditorium_type_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 30)
    private AuditoriumKind kind;

    @Column(name = "row_count", nullable = false)
    private short rowCount;

    @Column(name = "column_count", nullable = false)
    private short columnCount;

    @Column(name = "base_price", nullable = false)
    private int basePrice;

    @Builder
    public AuditoriumType(AuditoriumKind kind, short rowCount, short columnCount, int basePrice) {
        requirePositivePrice(basePrice);
        this.kind = kind;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.basePrice = basePrice;
    }

    public void updateBasePrice(int basePrice) {
        requirePositivePrice(basePrice);
        this.basePrice = basePrice;
    }

    private static void requirePositivePrice(int basePrice) {
        if (basePrice <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }
}
