package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.shop.domain.*;
import com.ceos24.springboot.shop.dto.*;
import com.ceos24.springboot.shop.repository.*;
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
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {
    private final Clock clock;
    private final StoreInventoryRepository storeInventoryRepository;
    private final MenuRepository menuRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;
    private final StoreOrderExpirationService expirationService;
    private final StoreOrderLifecycleService lifecycle;

    @Transactional
    public InventoryResponse createInventory(Long theaterId, InventoryCreateRequest request) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));
        Menu menu = menuRepository.findById(request.menuId())
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_INVENTORY_NOT_FOUND));
        if (request.stock() == null || request.stock() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_STORE_ORDER_REQUEST);
        }
        if (storeInventoryRepository.existsByTheaterAndMenu(theater, menu)) {
            throw new IllegalArgumentException("이미 등록된 매점 메뉴입니다.");
        }
        return InventoryResponse.from(storeInventoryRepository.save(StoreInventory.builder()
                .theater(theater).menu(menu).stock(request.stock()).build()));
    }

    public List<InventoryResponse> getInventories(Long theaterId) {
        expirationService.releaseExpiredForTheater(theaterId);
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));
        return storeInventoryRepository.findAllByTheater(theater).stream()
                .map(InventoryResponse::from).toList();
    }

    @Transactional
    public InventoryResponse addStock(Long theaterId, Long inventoryId, Integer quantity) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));
        StoreInventory inventory = storeInventoryRepository.findByInventoryIdAndTheater(inventoryId, theater)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_INVENTORY_NOT_FOUND));
        inventory.increaseStock(quantity);
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public StoreOrderResponse createOrder(Long userId, StoreOrderCreateRequest request) {
        Map<Long, Integer> quantities = validatedQuantities(request);
        // Finish expired holds before taking any inventory locks.
        expirationService.releaseExpiredForTheater(request.theaterId());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_USER_REQUIRED));
        Theater theater = theaterRepository.findById(request.theaterId())
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));

        List<OrderMenu> orderMenus = new ArrayList<>();
        int totalAmount = 0;
        try {
            // Sorted, merged IDs prevent reverse lock order and duplicate order lines.
            for (var entry : quantities.entrySet()) {
                StoreInventory inventory = storeInventoryRepository
                        .findWithLock(theater.getTheaterId(), entry.getKey())
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
        return StoreOrderResponse.from(order, orderItemRepository.saveAll(items));
    }

    public StoreOrderResponse getOrder(Long userId, Long orderId) {
        expirationService.releaseExpiredForUser(userId);
        StoreOrder order = storeOrderRepository.findByOrderIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND));
        return StoreOrderResponse.from(order, orderItemRepository.findAllByOrderOrderByMenu_MenuIdAsc(order));
    }

    @Transactional
    public StoreOrderResponse cancelOrder(Long userId, Long orderId) {
        StoreOrder order = storeOrderRepository.findMyOrderWithLock(orderId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND));
        lifecycle.expireLocked(order, LocalDateTime.now(clock));
        lifecycle.cancelLocked(order);
        return StoreOrderResponse.from(order, orderItemRepository.findAllByOrderOrderByMenu_MenuIdAsc(order));
    }

    private Map<Long, Integer> validatedQuantities(StoreOrderCreateRequest request) {
        if (request == null || request.theaterId() == null || request.theaterId() <= 0
                || request.items() == null || request.items().isEmpty()) {
            throw new GlobalException(ErrorCode.INVALID_STORE_ORDER_REQUEST);
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            for (OrderItemRequest item : request.items()) {
                if (item == null || item.menuId() == null || item.menuId() <= 0
                        || item.quantity() == null || item.quantity() <= 0) {
                    throw new GlobalException(ErrorCode.INVALID_STORE_ORDER_REQUEST);
                }
                quantities.merge(item.menuId(), item.quantity(), Math::addExact);
            }
        } catch (ArithmeticException e) {
            throw new GlobalException(ErrorCode.INVALID_STORE_ORDER_REQUEST);
        }
        return quantities;
    }

    private record OrderMenu(Menu menu, Integer quantity) {}
}
