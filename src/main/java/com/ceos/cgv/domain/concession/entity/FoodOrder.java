package com.ceos.cgv.domain.concession.entity;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.concession.enums.FoodOrderPaymentStatus;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "food_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_food_order_checkout_key",
                columnNames = {"user_id", "checkout_request_key"}),
        indexes = @Index(name = "idx_food_order_checkout_expiry",
                columnList = "payment_status, hold_expires_at, order_id"))
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

    @Column(name = "checkout_request_key", length = 36, updatable = false)
    private String checkoutRequestKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_status", length = 24)
    private FoodOrderPaymentStatus paymentStatus;

    @Column(name = "hold_expires_at", updatable = false)
    private Instant holdExpiresAt;

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

    public static FoodOrder createCheckout(
            User user, Cinema cinema, List<ItemSelection> selections, UUID requestKey, Instant expiresAt) {
        if (requestKey == null || expiresAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        FoodOrder order = create(user, cinema, selections);
        if (order.totalPrice <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        order.checkoutRequestKey = requestKey.toString();
        order.holdExpiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
        order.paymentStatus = FoodOrderPaymentStatus.HELD;
        return order;
    }

    public void startPayment(Instant now) {
        if (paymentStatus == FoodOrderPaymentStatus.EXPIRED || isHeldAndElapsed(now)) {
            throw new BusinessException(ErrorCode.FOOD_ORDER_EXPIRED);
        }
        requireStatus(FoodOrderPaymentStatus.HELD);
        paymentStatus = FoodOrderPaymentStatus.PAYMENT_PENDING;
    }

    public void confirmPayment() {
        requireStatus(FoodOrderPaymentStatus.PAYMENT_PENDING);
        paymentStatus = FoodOrderPaymentStatus.PAID;
    }

    public void releaseFailedPayment() {
        requireStatus(FoodOrderPaymentStatus.PAYMENT_PENDING);
        paymentStatus = FoodOrderPaymentStatus.RELEASED;
    }

    public boolean releaseCheckout(Instant now) {
        if (paymentStatus == FoodOrderPaymentStatus.RELEASED || paymentStatus == FoodOrderPaymentStatus.EXPIRED) {
            return false;
        }
        requireStatus(FoodOrderPaymentStatus.HELD);
        if (!expireIfElapsed(now)) {
            paymentStatus = FoodOrderPaymentStatus.RELEASED;
        }
        return true;
    }

    public boolean expireIfElapsed(Instant now) {
        if (!isHeldAndElapsed(now)) {
            return false;
        }
        paymentStatus = FoodOrderPaymentStatus.EXPIRED;
        return true;
    }

    public void requirePaid() {
        requireStatus(FoodOrderPaymentStatus.PAID);
    }

    public void cancelPayment() {
        requirePaid();
        paymentStatus = FoodOrderPaymentStatus.CANCELED;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private boolean isHeldAndElapsed(Instant now) {
        return paymentStatus == FoodOrderPaymentStatus.HELD && !now.isBefore(holdExpiresAt);
    }

    private void requireStatus(FoodOrderPaymentStatus expected) {
        if (paymentStatus != expected) {
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
    }

    public record ItemSelection(Product product, Integer quantity) {
        public ItemSelection {
            if (product == null || quantity == null || quantity <= 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }
}
