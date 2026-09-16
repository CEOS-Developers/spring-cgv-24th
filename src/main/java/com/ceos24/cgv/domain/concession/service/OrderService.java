package com.ceos24.cgv.domain.concession.service;

import com.ceos24.cgv.domain.concession.dto.request.OrderCreateRequest;
import com.ceos24.cgv.domain.concession.dto.response.OrderItemResponse;
import com.ceos24.cgv.domain.concession.dto.response.OrderResponse;
import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.entity.OrderItem;
import com.ceos24.cgv.domain.concession.exception.ConcessionErrorStatus;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import com.ceos24.cgv.domain.concession.repository.OrderItemRepository;
import com.ceos24.cgv.domain.concession.repository.OrderRepository;
import com.ceos24.cgv.domain.concession.repository.StockRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockRepository stockRepository;
    private final ItemRepository itemRepository;
    private final TheaterRepository theaterRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long userId, OrderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));
        Theater theater = theaterRepository.findById(request.theaterId())
                .orElseThrow(() -> new GeneralException(TheaterErrorStatus.THEATER_NOT_FOUND));

        Map<Long, Integer> quantityByItemId = new LinkedHashMap<>();
        for (var itemRequest : request.items()) {
            quantityByItemId.merge(itemRequest.itemId(), itemRequest.quantity(), Integer::sum);
        }

        int totalPrice = 0;
        for (Map.Entry<Long, Integer> entry : quantityByItemId.entrySet()) {
            Item item = itemRepository.findById(entry.getKey())
                    .orElseThrow(() -> new GeneralException(ConcessionErrorStatus.ITEM_NOT_FOUND));
            stockRepository.findByTheaterIdAndItemId(request.theaterId(), item.getId())
                    .orElseThrow(() -> new GeneralException(ConcessionErrorStatus.STOCK_NOT_FOUND));

            totalPrice += item.getPrice() * entry.getValue();
        }

        Order order = orderRepository.save(Order.create(user, theater, totalPrice));

        for (Map.Entry<Long, Integer> entry : quantityByItemId.entrySet()) {
            Item item = itemRepository.findById(entry.getKey())
                    .orElseThrow(() -> new GeneralException(ConcessionErrorStatus.ITEM_NOT_FOUND));

            int updatedRows = stockRepository.decreaseStock(request.theaterId(), item.getId(), entry.getValue());
            if (updatedRows == 0) {
                throw new GeneralException(ConcessionErrorStatus.INSUFFICIENT_STOCK);
            }

            OrderItem orderItem = OrderItem.create(order, item, entry.getValue(), item.getPrice());
            orderItemRepository.save(orderItem);
        }

        return order.getId();
    }

    public OrderResponse findById(Long orderId, Long userId) {
        Order order = orderRepository.findByIdWithTheater(orderId)
                .orElseThrow(() -> new GeneralException(ConcessionErrorStatus.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new GeneralException(ConcessionErrorStatus.NOT_ORDER_OWNER);
        }

        List<OrderItemResponse> items = orderItemRepository.findByOrderIdWithItem(orderId).stream()
                .map(OrderItemResponse::from)
                .toList();

        return OrderResponse.of(order, items);
    }

    public List<OrderResponse> findByUserId(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        List<Order> orders = orderRepository.findByUserIdWithTheaterOrderByOrderedAtDesc(userId);
        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        Map<Long, List<OrderItemResponse>> itemsByOrderId = orderItemRepository
                .findByOrderIdInWithItem(orderIds).stream()
                .collect(Collectors.groupingBy(
                        oi -> oi.getOrder().getId(),
                        Collectors.mapping(OrderItemResponse::from, Collectors.toList())
                ));

        return orders.stream()
                .map(order -> OrderResponse.of(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
    }
}
