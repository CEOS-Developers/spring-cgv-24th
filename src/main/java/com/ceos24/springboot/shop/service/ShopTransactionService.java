package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.shop.domain.Menu;
import com.ceos24.springboot.shop.domain.OrderItem;
import com.ceos24.springboot.shop.domain.StoreInventory;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.dto.InventoryResponse;
import com.ceos24.springboot.shop.dto.StoreOrderResponse;
import com.ceos24.springboot.shop.repository.OrderItemRepository;
import com.ceos24.springboot.shop.repository.StoreInventoryRepository;
import com.ceos24.springboot.shop.repository.StoreOrderRepository;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.theater.repository.TheaterRepository;
import com.ceos24.springboot.user.domain.User;
import com.ceos24.springboot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

@Service
@RequiredArgsConstructor
public class ShopTransactionService {
    private final Clock clock;
    private final StoreInventoryRepository storeInventoryRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;
    private final InventoryCache inventoryCache;

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventories(Long theaterId) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));
        return storeInventoryRepository.findAllByTheater(theater).stream()
                .map(InventoryResponse::from).toList();
    }

    @Transactional
    public StoreOrderResponse createOrder(Long userId, Long theaterId, SortedMap<Long, Integer> quantities) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_USER_REQUIRED));
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));

        List<OrderMenu> orderMenus = new ArrayList<>();
        int totalAmount = 0;
        try {
            for (var entry : quantities.entrySet()) {
                StoreInventory inventory = storeInventoryRepository
                        .findWithLock(theaterId, entry.getKey())
                        .orElseThrow(() -> new GlobalException(ErrorCode.STORE_INVENTORY_NOT_FOUND));
                Menu menu = inventory.getMenu();
                if (menu.getMenuPrice() == null || menu.getMenuPrice() <= 0) {
                    throw new GlobalException(ErrorCode.INVALID_PAYMENT_AMOUNT);
                }
                inventory.decreaseStock(entry.getValue());
                totalAmount = Math.addExact(totalAmount,
                        Math.multiplyExact(menu.getMenuPrice(), entry.getValue()));
                orderMenus.add(new OrderMenu(menu, entry.getValue()));
            }
        } catch (ArithmeticException e) {
            throw new GlobalException(ErrorCode.INVALID_STORE_ORDER_REQUEST);
        }

        StoreOrder order = storeOrderRepository.save(StoreOrder.builder()
                .user(user).theater(theater).totalAmount(totalAmount)
                .now(LocalDateTime.now(clock)).build());
        List<OrderItem> items = orderMenus.stream().map(item -> OrderItem.builder()
                .order(order).menu(item.menu()).quantity(item.quantity()).build()).toList();
        inventoryCache.invalidateAfterCommit(theaterId);
        return StoreOrderResponse.from(order, orderItemRepository.saveAll(items));
    }

    @Transactional(readOnly = true)
    public StoreOrderResponse getOrder(Long userId, Long orderId) {
        StoreOrder order = storeOrderRepository.findByOrderIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND));
        return StoreOrderResponse.from(order, orderItemRepository.findAllByOrderOrderByMenu_MenuIdAsc(order));
    }

    private record OrderMenu(Menu menu, Integer quantity) {}
}
