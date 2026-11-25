package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.shop.domain.*;
import com.ceos24.springboot.shop.dto.*;
import com.ceos24.springboot.shop.repository.*;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.theater.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ShopService {
    private final Clock clock;
    private final StoreInventoryRepository storeInventoryRepository;
    private final MenuRepository menuRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TheaterRepository theaterRepository;
    private final StoreOrderExpirationService expirationService;
    private final ShopTransactionService transactions;
    private final StoreOrderLifecycleService lifecycle;
    private final InventoryCache inventoryCache;

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
        StoreInventory inventory = storeInventoryRepository.save(StoreInventory.builder()
                .theater(theater).menu(menu).stock(request.stock()).build());
        inventoryCache.invalidateAfterCommit(theaterId);
        return InventoryResponse.from(inventory);
    }

    @Transactional(propagation = Propagation.NEVER)
    public List<InventoryResponse> getInventories(Long theaterId) {
        expirationService.releaseExpiredForTheater(theaterId);
        return inventoryCache.get(theaterId, () -> transactions.getInventories(theaterId));
    }

    @Transactional
    public InventoryResponse addStock(Long theaterId, Long inventoryId, Integer quantity) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new GlobalException(ErrorCode.THEATER_NOT_FOUND));
        StoreInventory inventory = storeInventoryRepository.findByInventoryIdAndTheater(inventoryId, theater)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_INVENTORY_NOT_FOUND));
        inventory.increaseStock(quantity);
        inventoryCache.invalidateAfterCommit(theaterId);
        return InventoryResponse.from(inventory);
    }

    @Transactional(propagation = Propagation.NEVER)
    public StoreOrderResponse createOrder(Long userId, StoreOrderCreateRequest request) {
        SortedMap<Long, Integer> quantities = validatedQuantities(request);
        expirationService.releaseExpiredForTheater(request.theaterId());
        return transactions.createOrder(userId, request.theaterId(), quantities);
    }

    @Transactional(propagation = Propagation.NEVER)
    public StoreOrderResponse getOrder(Long userId, Long orderId) {
        expirationService.releaseExpiredForUser(userId);
        return transactions.getOrder(userId, orderId);
    }

    @Transactional
    public StoreOrderResponse cancelOrder(Long userId, Long orderId) {
        StoreOrder order = storeOrderRepository.findMyOrderWithLock(orderId, userId)
                .orElseThrow(() -> new GlobalException(ErrorCode.STORE_ORDER_NOT_FOUND));
        lifecycle.expireLocked(order, LocalDateTime.now(clock));
        lifecycle.cancelLocked(order);
        return StoreOrderResponse.from(order, orderItemRepository.findAllByOrderOrderByMenu_MenuIdAsc(order));
    }

    private SortedMap<Long, Integer> validatedQuantities(StoreOrderCreateRequest request) {
        if (request == null || request.theaterId() == null || request.theaterId() <= 0
                || request.items() == null || request.items().isEmpty()) {
            throw new GlobalException(ErrorCode.INVALID_STORE_ORDER_REQUEST);
        }
        SortedMap<Long, Integer> quantities = new TreeMap<>();
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
}
