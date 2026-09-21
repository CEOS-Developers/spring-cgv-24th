package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.shop.domain.Menu;
import com.ceos24.springboot.shop.domain.OrderItem;
import com.ceos24.springboot.shop.domain.StoreInventory;
import com.ceos24.springboot.shop.domain.StoreOrder;
import com.ceos24.springboot.shop.dto.*;
import com.ceos24.springboot.shop.repository.MenuRepository;
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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final StoreInventoryRepository storeInventoryRepository;
    private final MenuRepository menuRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final OrderItemRepository orderItemRepository;

    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;


    // 영화관 매점 재고 등록
    @Transactional
    public InventoryResponse createInventory(
            Long theaterId,
            InventoryCreateRequest request
    ) {

        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화관을 찾을 수 없습니다."
                        )
                );

        Menu menu = menuRepository.findById(request.menuId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 메뉴를 찾을 수 없습니다."
                        )
                );

        if (request.stock() == null || request.stock() <= 0) {
            throw new IllegalArgumentException(
                    "재고 수량은 1개 이상이어야 합니다."
            );
        }

        if (storeInventoryRepository
                .existsByTheaterAndMenu(theater, menu)) {

            throw new IllegalArgumentException(
                    "이미 등록된 매점 메뉴입니다."
            );
        }

        StoreInventory inventory = StoreInventory.builder()
                .theater(theater)
                .menu(menu)
                .stock(request.stock())
                .build();

        StoreInventory savedInventory =
                storeInventoryRepository.save(inventory);

        return InventoryResponse.from(savedInventory);
    }


    // 특정 영화관의 매점 재고 조회
    public List<InventoryResponse> getInventories(
            Long theaterId
    ) {

        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화관을 찾을 수 없습니다."
                        )
                );

        return storeInventoryRepository.findAllByTheater(theater)
                .stream()
                .map(InventoryResponse::from)
                .toList();
    }


    // 매점 주문
    @Transactional
    public StoreOrderResponse createOrder(
            Long userId,
            StoreOrderCreateRequest request
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 사용자를 찾을 수 없습니다."
                        )
                );

        Theater theater =
                theaterRepository.findById(request.theaterId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 영화관을 찾을 수 없습니다."
                                )
                        );

        if (request.items() == null
                || request.items().isEmpty()) {

            throw new IllegalArgumentException(
                    "주문할 메뉴가 없습니다."
            );
        }

        int totalAmount = 0;

        List<OrderMenu> orderMenus = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.items()) {

            Menu menu =
                    menuRepository.findById(itemRequest.menuId())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "해당 메뉴를 찾을 수 없습니다."
                                    )
                            );

            StoreInventory inventory =
                    storeInventoryRepository
                            .findByTheaterAndMenu(theater, menu)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "해당 영화관에서 판매하지 않는 메뉴입니다."
                                    )
                            );

            // 재고 확인 + 차감
            inventory.decreaseStock(
                    itemRequest.quantity()
            );

            totalAmount +=
                    menu.getMenuPrice()
                            * itemRequest.quantity();

            orderMenus.add(
                    new OrderMenu(
                            menu,
                            itemRequest.quantity()
                    )
            );
        }

        // 주문 저장
        StoreOrder order = StoreOrder.builder()
                .user(user)
                .theater(theater)
                .totalAmount(totalAmount)
                .build();

        StoreOrder savedOrder =
                storeOrderRepository.save(order);

        // 주문 상품 저장
        List<OrderItem> savedItems = new ArrayList<>();

        for (OrderMenu orderMenu : orderMenus) {

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .menu(orderMenu.menu())
                    .quantity(orderMenu.quantity())
                    .build();

            savedItems.add(
                    orderItemRepository.save(orderItem)
            );
        }

        return StoreOrderResponse.from(
                savedOrder,
                savedItems
        );
    }


    // 매점 주문 내역 조회
    public StoreOrderResponse getOrder(Long orderId) {

        StoreOrder order =
                storeOrderRepository.findById(orderId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 주문을 찾을 수 없습니다."
                                )
                        );

        List<OrderItem> items =
                orderItemRepository.findAllByOrder(order);

        return StoreOrderResponse.from(
                order,
                items
        );
    }


    // 주문 처리 중 메뉴와 수량을 임시 보관
    private record OrderMenu(
            Menu menu,
            Integer quantity
    ) {
    }
}