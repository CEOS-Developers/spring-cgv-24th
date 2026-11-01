package com.ceos24.cgv.domain.order.service;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.order.dto.request.CreateOrderRequest;
import com.ceos24.cgv.domain.order.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.order.entity.Order;
import com.ceos24.cgv.domain.order.entity.OrderItem;
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
    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final MenuStockRepository menuStockRepository;

    @Transactional
    public void createOrder(Long memberId, CreateOrderRequest request) {
        Member member = findMember(memberId);
        Store store = findStore(request.storeId());

        Map<Long, MenuStock> menuStockMap = findAndValidateMenuStocks(request.storeId(), request.items());
        List<OrderItem> orderItems = processOrderItemsAndDeductStock(request.items(), menuStockMap);

        Order order = Order.create(member, store, orderItems);
        orderRepository.save(order);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private Map<Long, MenuStock> findAndValidateMenuStocks(Long storeId, List<OrderItemRequest> items) {
        List<Long> menuIds =
                items.stream().map(OrderItemRequest::menuId).distinct().toList();

        List<MenuStock> menuStocks = menuStockRepository.findAllByStoreIdAndMenuIdIn(storeId, menuIds);

        if (menuStocks.size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }

        return menuStocks.stream().collect(Collectors.toMap(ms -> ms.getMenu().getId(), ms -> ms));
    }

    private List<OrderItem> processOrderItemsAndDeductStock(
            List<OrderItemRequest> items, Map<Long, MenuStock> menuStockMap) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : items) {
            MenuStock menuStock = menuStockMap.get(itemRequest.menuId());
            menuStock.decreaseStock(itemRequest.quantity());

            OrderItem orderItem = OrderItem.create(menuStock.getMenu(), itemRequest.quantity());
            orderItems.add(orderItem);
        }
        return orderItems;
    }
}
