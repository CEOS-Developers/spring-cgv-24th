package com.cgvclone.cgv.domain.cinema_keeping;

import com.cgvclone.cgv.common.BaseEntity;
import com.cgvclone.cgv.domain.cinema.Cinema;
import com.cgvclone.cgv.domain.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cinema_keepings", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_cinema_keeping_user_cinema",
                columnNames = {"user_id", "cinema_id"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CinemaKeeping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cinemaKeepingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Builder
    public CinemaKeeping(User user, Cinema cinema) {
        this.user = user;
        this.cinema = cinema;
    }
}
