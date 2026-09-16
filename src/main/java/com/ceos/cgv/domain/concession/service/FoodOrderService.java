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
        // 주문을 요청한 사용자가 실제로 존재하는지 확인
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 주문을 처리할 영화관이 실제로 존재하는지 확인
        Cinema cinema = cinemaRepository.findById(request.cinemaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        // 같은 상품이 요청에 여러 번 들어올 수 있으므로 상품별 총수량으로 합침
        Map<Long, Integer> quantitiesByProduct = new LinkedHashMap<>();
        for (FoodOrderItemRequest item : request.items()) {
            quantitiesByProduct.merge(item.productId(), item.quantity(), Math::addExact);
        }

        // 이후 재고 차감과 주문 항목 생성에 사용할 상품별 처리 정보를 저장
        List<OrderLine> orderLines = new ArrayList<>();

        // 상품을 하나씩 조회하지 않고 모든 상품을 한 번의 쿼리로 조회
        Map<Long, Product> productsById = productRepository.findAllById(quantitiesByProduct.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        long totalPrice = 0;
        for (Map.Entry<Long, Integer> item : quantitiesByProduct.entrySet()) {
            // 상품 id로 조회한 상품을 가져옴
            Product product = productsById.get(item.getKey());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            // 동시에 같은 재고를 차감하지 못하도록 영화관별 상품 재고 행을 잠금 조회
            Inventory inventory = inventoryRepository.findByCinema_IdAndProduct_IdForUpdate(cinema.getId(), product.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
            // 잠금을 획득한 뒤 최신 재고를 확인
            if (inventory.getStockQuantity() < item.getValue()) {
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
            }
            // 주문 시점의 상품 가격과 수량으로 총 주문 금액을 계산
            totalPrice = Math.addExact(totalPrice, Math.multiplyExact(product.getPrice(), item.getValue()));
            // 검증이 끝난 상품·재고·수량을 나중에 재사용할 수 있도록 보관
            orderLines.add(new OrderLine(product, inventory, item.getValue()));
        }

        // 모든 상품과 재고 검증이 끝난 뒤 주문 엔티티 객체를 생성함
        FoodOrder order = FoodOrder.builder()
                .user(user)
                .cinema(cinema)
                .totalPrice(totalPrice)
                .build();
        for (OrderLine line : orderLines) {
            // 검증이 끝난 재고에서 주문 수량만큼 차감
            line.inventory().decrease(line.quantity());
            // 주문 엔티티 객체와 상품을 주문 항목 객체로 연결함
            order.addItem(OrderItem.builder()
                    .foodOrder(order)
                    .product(line.product())
                    .quantity(line.quantity())
                    .build());
        }
        // cascade 설정으로 주문과 주문 항목을 함께 저장
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
