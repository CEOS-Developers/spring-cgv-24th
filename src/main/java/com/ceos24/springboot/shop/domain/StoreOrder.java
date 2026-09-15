package com.ceos24.springboot.shop.domain;

import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "store_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOrder {
//  사용자가 매점에서 구매한 내역

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long OrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    @Column(name = "total_amount")
    private Integer totalAmount;

    @CreationTimestamp
    @Column(name = "order_at", updatable = false)
    private LocalDateTime orderAt;
}