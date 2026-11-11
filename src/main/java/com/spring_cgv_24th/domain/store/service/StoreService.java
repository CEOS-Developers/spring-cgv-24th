package com.spring_cgv_24th.domain.store.service;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.store.dto.ProductResDTO;
import com.spring_cgv_24th.domain.store.dto.StoreOrderReqDTO;
import com.spring_cgv_24th.domain.store.dto.StoreOrderResDTO;
import com.spring_cgv_24th.domain.store.dto.StoreStockReqDTO;
import com.spring_cgv_24th.domain.store.dto.TheaterStockResDTO;
import com.spring_cgv_24th.domain.store.entity.Product;
import com.spring_cgv_24th.domain.store.entity.StoreOrder;
import com.spring_cgv_24th.domain.store.entity.StoreOrderItem;
import com.spring_cgv_24th.domain.store.entity.TheaterStock;
import com.spring_cgv_24th.domain.store.enums.StoreOrderStatus;
import com.spring_cgv_24th.domain.store.repository.ProductRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderItemRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderRepository;
import com.spring_cgv_24th.domain.store.repository.TheaterStockRepository;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final ProductRepository productRepository;
    private final TheaterStockRepository theaterStockRepository;
    private final TheaterRepository theaterRepository;
    private final MemberRepository memberRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final StoreOrderItemRepository storeOrderItemRepository;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public List<ProductResDTO> getProducts() {
        return productRepository.findAll(Sort.by("id")).stream()
                .map(ProductResDTO::from)
                .toList();
    }

    public List<TheaterStockResDTO> getTheaterProducts(Long theaterId) {
        if (!theaterRepository.existsById(theaterId)) {
            throw new CustomException(ErrorCode.THEATER_NOT_FOUND);
        }

        return theaterStockRepository.findAllByTheater_IdOrderByProduct_IdAsc(theaterId).stream()
                .map(stock -> TheaterStockResDTO.from(stock.getProduct(), stock.getQuantity()))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TheaterStockResDTO replenishStock(Long theaterId, Long productId, StoreStockReqDTO request) {
        Integer additionalQuantity = request.quantity();
        if (additionalQuantity == null || additionalQuantity <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // 재고 행이 아직 없을 때도 동일 영화관의 등록 요청을 직렬화한다.
        Theater theater = theaterRepository.findByIdForUpdate(theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_NOT_FOUND));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        TheaterStock stock = theaterStockRepository.findByTheaterIdAndProductIdForUpdate(theaterId, productId)
                .orElse(null);

        if (stock == null) {
            stock = theaterStockRepository.save(TheaterStock.builder()
                    .theater(theater)
                    .product(product)
                    .quantity(additionalQuantity)
                    .build());
        } else {
            stock.addQuantity(additionalQuantity);
        }
        return TheaterStockResDTO.from(product, stock.getQuantity());
    }

    @Transactional
    public StoreOrderResDTO createOrder(Long theaterId, Long memberId, StoreOrderReqDTO.CreateOrderDTO request) {
        validateUniqueProducts(request.items());

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_NOT_FOUND));

        StoreOrder order = StoreOrder.builder()
                .member(member)
                .theater(theater)
                .purchasedAt(LocalDateTime.now(clock))
                .build();
        List<StoreOrderItem> items = reserveStockAndCreateItems(theaterId, order, request.items());

        storeOrderRepository.save(order);
        return StoreOrderResDTO.from(order, storeOrderItemRepository.saveAll(items));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void releaseUnpaidOrderStock() {
        LocalDateTime now = LocalDateTime.now(clock);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> orderIds = transaction.execute(status -> storeOrderRepository.findOrderIdsForStockRelease(
                StoreOrderStatus.PENDING_PAYMENT, StoreOrderStatus.PAYMENT_FAILED, now));
        for (Long orderId : orderIds) {
            try {
                transaction.executeWithoutResult(status -> {
                    StoreOrder order = storeOrderRepository.findByIdForUpdate(orderId).orElse(null);
                    if (order != null && order.releaseStock(now)) {
                        restoreStock(order);
                    }
                });
            } catch (RuntimeException error) {
                log.warn("미결제 주문 재고 복원 실패: orderId={}", orderId, error);
            }
        }
    }

    @Transactional
    public void cancelPaidOrder(StoreOrder order, Map<Long, Integer> quantities, boolean fullyCancelled) {
        for (StoreOrderItem item : storeOrderItemRepository.findAllByOrder_IdOrderByProduct_IdAsc(order.getId())) {
            int quantity = quantities.isEmpty() ? item.getRemainingQuantity()
                    : quantities.getOrDefault(item.getProduct().getId(), 0);
            if (quantity == 0) continue;
            lockStock(order.getTheater().getId(), item.getProduct().getId()).addQuantity(quantity);
            item.cancelQuantity(quantity);
        }
        if (fullyCancelled) order.cancelPayment();
        else order.partiallyCancelPayment();
    }

    public long calculateCancellationAmount(StoreOrder order, Map<Long, Integer> quantities) {
        List<StoreOrderItem> items = storeOrderItemRepository.findAllByOrder_IdOrderByProduct_IdAsc(order.getId());
        long amount = 0;
        int matched = 0;
        for (StoreOrderItem item : items) {
            Integer quantity = quantities.get(item.getProduct().getId());
            if (quantity == null) continue;
            if (quantity <= 0 || quantity > item.getRemainingQuantity()) {
                throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
            }
            amount += (long) item.getUnitPrice() * quantity;
            matched++;
        }
        if (matched != quantities.size()) throw new CustomException(ErrorCode.PAYMENT_CANCEL_INVALID);
        return amount;
    }

    private void restoreStock(StoreOrder order) {
        for (StoreOrderItem item : storeOrderItemRepository.findAllByOrder_IdOrderByProduct_IdAsc(order.getId())) {
            TheaterStock stock = lockStock(order.getTheater().getId(), item.getProduct().getId());
            stock.addQuantity(item.getQuantity());
        }
    }

    private void validateUniqueProducts(List<StoreOrderReqDTO.OrderItemDTO> items) {
        List<Long> productIds = items.stream().map(StoreOrderReqDTO.OrderItemDTO::productId).toList();
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private List<StoreOrderItem> reserveStockAndCreateItems(
            Long theaterId, StoreOrder order, List<StoreOrderReqDTO.OrderItemDTO> orderItems) {
        List<StoreOrderItem> items = new ArrayList<>();

        // 동시 구매 시 재고 행의 잠금 순서가 엇갈리지 않도록 상품 ID 오름차순으로 조회한다.
        for (StoreOrderReqDTO.OrderItemDTO item : orderItems.stream()
                .sorted(Comparator.comparing(StoreOrderReqDTO.OrderItemDTO::productId)).toList()) {
            TheaterStock stock = lockStock(theaterId, item.productId());
            stock.decreaseQuantity(item.quantity());
            items.add(StoreOrderItem.builder()
                    .order(order)
                    .product(stock.getProduct())
                    .quantity(item.quantity())
                    .build());
        }

        return items;
    }

    private TheaterStock lockStock(Long theaterId, Long productId) {
        return theaterStockRepository.findByTheaterIdAndProductIdForUpdate(theaterId, productId)
                .orElseThrow(() -> new CustomException(productRepository.existsById(productId)
                        ? ErrorCode.THEATER_STOCK_NOT_FOUND : ErrorCode.PRODUCT_NOT_FOUND));
    }
}
