package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_theaters",
        uniqueConstraints = {
                @UniqueConstraint(
                        //한 사용자가 같은 영화관 중복 찜 불가 !
                        name = "uk_user_theater",
                        columnNames = {"user_id", "theater_id"}
                )
        }
)
public class UserTheater extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_theater_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;

    @Builder
    private UserTheater(
            User user,
            Theater theater
    ) {
        this.user = user;
        this.theater = theater;
    }
}