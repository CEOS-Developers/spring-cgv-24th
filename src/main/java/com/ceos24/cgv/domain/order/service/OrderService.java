package com.ceos24.cgv.domain.order.service;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.order.dto.request.CreateOrderRequest;
import com.ceos24.cgv.domain.order.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.entity.OrderItem;
import com.ceos24.cgv.domain.order.repository.OrderItemRepository;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.store.entity.MenuStock;
import com.ceos24.cgv.domain.store.entity.Store;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final MenuStockRepository menuStockRepository;

    @Transactional
    public void createOrder(Long memberId, CreateOrderRequest request) {
        Member member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Store store = storeRepository
                .findById(request.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        List<Long> menuIds = request.items().stream()
                .map(OrderItemRequest::menuId)
                .distinct()
                .toList();

        List<MenuStock> menuStocks = menuStockRepository.findAllByStoreIdAndMenuIdIn(request.storeId(), menuIds);

        if (menuStocks.size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }

        Map<Long, MenuStock> menuStockMap =
                menuStocks.stream().collect(Collectors.toMap(ms -> ms.getMenu().getId(), ms -> ms));

        long totalPrice = 0L;

        for (OrderItemRequest itemRequest : request.items()) {

            MenuStock menuStock = menuStockMap.get(itemRequest.menuId());

            menuStock.decreaseStock(itemRequest.quantity());

            long price = menuStock.getMenu().getPrice();
            totalPrice += price * itemRequest.quantity();
        }

        Order order = new Order(member, store, totalPrice);
        orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.items()) {

            MenuStock menuStock = menuStockMap.get(itemRequest.menuId());

            OrderItem orderItem = new OrderItem(
                    order,
                    menuStock.getMenu(),
                    itemRequest.quantity(),
                    menuStock.getMenu().getPrice());
            orderItems.add(orderItem);
        }

        orderItemRepository.saveAll(orderItems);
    }
}
