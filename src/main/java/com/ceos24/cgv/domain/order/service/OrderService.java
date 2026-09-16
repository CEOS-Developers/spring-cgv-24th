package com.ceos24.cgv.domain.order.service;

import com.ceos24.cgv.domain.member.domain.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.order.domain.Order;
import com.ceos24.cgv.domain.order.domain.OrderItem;
import com.ceos24.cgv.domain.order.dto.request.CreateOrderRequest;
import com.ceos24.cgv.domain.order.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.order.repository.OrderItemRepository;
import com.ceos24.cgv.domain.order.repository.OrderRepository;
import com.ceos24.cgv.domain.store.domain.MenuStock;
import com.ceos24.cgv.domain.store.domain.Store;
import com.ceos24.cgv.domain.store.repository.MenuStockRepository;
import com.ceos24.cgv.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 스토어입니다."));

        long totalPrice = 0L;
        List<MenuStock> menuStocks = new ArrayList<>();
        
        for (OrderItemRequest itemRequest : request.items()) {
            MenuStock menuStock = menuStockRepository.findByStoreIdAndMenuId(request.storeId(), itemRequest.menuId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 스토어에 존재하지 않는 메뉴입니다."));
            
            menuStock.decreaseStock(itemRequest.quantity());
            
            long price = menuStock.getMenu().getPrice();
            totalPrice += price * itemRequest.quantity();
            menuStocks.add(menuStock);
        }

        Order order = new Order(member, store, totalPrice);
        orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest itemRequest = request.items().get(i);
            MenuStock menuStock = menuStocks.get(i);
            
            OrderItem orderItem = new OrderItem(order, menuStock.getMenu(), itemRequest.quantity(), menuStock.getMenu().getPrice());
            orderItems.add(orderItem);
        }

        orderItemRepository.saveAll(orderItems);
    }
}
