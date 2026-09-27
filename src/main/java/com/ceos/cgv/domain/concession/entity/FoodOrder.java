package com.ceos.cgv.domain.concession.entity;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Objects;

@Entity
@Table(name = "food_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "foodOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    private FoodOrder(User user, Cinema cinema) {
        this.user = user;
        this.cinema = cinema;
        this.totalPrice = 0L;
        this.createdAt = LocalDateTime.now();
    }

    public static FoodOrder create(User user, Cinema cinema, List<ItemSelection> selections) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(cinema);
        if (selections == null || selections.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        FoodOrder order = new FoodOrder(user, cinema);
        for (ItemSelection selection : selections) {
            if (selection == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            OrderItem item = new OrderItem(order, selection.product(), selection.quantity());
            order.totalPrice = Math.addExact(order.totalPrice, item.subtotal());
            order.items.add(item);
        }
        return order;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public record ItemSelection(Product product, Integer quantity) {
        public ItemSelection {
            if (product == null || quantity == null || quantity <= 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }
}
