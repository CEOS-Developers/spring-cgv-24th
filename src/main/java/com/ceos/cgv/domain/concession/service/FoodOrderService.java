package com.ceos.cgv.domain.concession.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos.cgv.domain.concession.dto.FoodOrderCreateRequest;
import com.ceos.cgv.domain.concession.dto.FoodOrderItemRequest;
import com.ceos.cgv.domain.concession.entity.FoodOrder;
import com.ceos.cgv.domain.concession.entity.Inventory;
import com.ceos.cgv.domain.concession.entity.OrderItem;
import com.ceos.cgv.domain.concession.entity.Product;
import com.ceos.cgv.domain.concession.repository.FoodOrderRepository;
import com.ceos.cgv.domain.concession.repository.InventoryRepository;
import com.ceos.cgv.domain.concession.repository.ProductRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodOrderService {
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final FoodOrderRepository foodOrderRepository;

    @Transactional
    public FoodOrder create(FoodOrderCreateRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Cinema cinema = cinemaRepository.findById(request.cinemaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        Map<Long, Integer> quantitiesByProduct = new LinkedHashMap<>();
        for (FoodOrderItemRequest item : request.items()) {
            quantitiesByProduct.merge(item.productId(), item.quantity(), Math::addExact);
        }

        List<OrderLine> orderLines = new ArrayList<>();
        Map<Long, Product> productsById = productRepository.findAllById(quantitiesByProduct.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        long totalPrice = 0;
        for (Map.Entry<Long, Integer> item : quantitiesByProduct.entrySet()) {
            Product product = productsById.get(item.getKey());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            Inventory inventory = inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(cinema.getId(), product.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
            if (inventory.getStockQuantity() < item.getValue()) {
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
            }
            totalPrice = Math.addExact(totalPrice, Math.multiplyExact(product.getPrice(), item.getValue()));
            orderLines.add(new OrderLine(product, inventory, item.getValue()));
        }

        FoodOrder order = FoodOrder.builder()
                .user(user)
                .cinema(cinema)
                .totalPrice(totalPrice)
                .build();
        for (OrderLine line : orderLines) {
            line.inventory().decrease(line.quantity());
            order.addItem(OrderItem.builder()
                    .foodOrder(order)
                    .product(line.product())
                    .quantity(line.quantity())
                    .build());
        }
        return foodOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public FoodOrder findById(Long orderId) {
        return foodOrderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_ORDER_NOT_FOUND));
    }

    private record OrderLine(Product product, Inventory inventory, Integer quantity) {
    }
}
