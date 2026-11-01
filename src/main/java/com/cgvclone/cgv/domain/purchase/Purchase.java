package com.cgvclone.cgv.domain.purchase;

import com.cgvclone.cgv.domain.cinema.Cinema;
import com.cgvclone.cgv.domain.goods.Goods;
import com.cgvclone.cgv.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false, updatable = false)
    private Cinema cinema;

    @Column(nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    @Column(nullable = false, updatable = false)
    private Integer totalPrice;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.PERSIST)
    private List<PurchaseGoods> purchaseGoods = new ArrayList<>();

    @Builder
    public Purchase(User user, Cinema cinema, Map<Goods, Integer> goodsQuantities) {
        this.user = Objects.requireNonNull(user, "구매자는 필수입니다.");
        this.cinema = Objects.requireNonNull(cinema, "극장은 필수입니다.");
        if (goodsQuantities == null || goodsQuantities.isEmpty()) {
            throw new IllegalArgumentException("구매 상품은 하나 이상이어야 합니다.");
        }
        this.totalPrice = 0;
        goodsQuantities.forEach((goods, quantity) -> {
            PurchaseGoods item = new PurchaseGoods(this, goods, quantity);
            this.totalPrice = Math.addExact(this.totalPrice, item.getTotalPrice());
            this.purchaseGoods.add(item);
        });
        this.purchasedAt = LocalDateTime.now();
    }

    public List<PurchaseGoods> getPurchaseGoods() {
        return Collections.unmodifiableList(purchaseGoods);
    }
}
