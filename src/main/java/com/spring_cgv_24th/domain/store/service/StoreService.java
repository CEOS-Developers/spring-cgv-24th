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
import com.spring_cgv_24th.domain.store.repository.ProductRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderItemRepository;
import com.spring_cgv_24th.domain.store.repository.StoreOrderRepository;
import com.spring_cgv_24th.domain.store.repository.TheaterStockRepository;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public StoreOrderResDTO createOrder(Long theaterId, StoreOrderReqDTO.CreateOrderDTO request) {
        List<Long> productIds = request.items().stream()
                .map(StoreOrderReqDTO.OrderItemDTO::productId)
                .toList();
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // 임시: 로그인 구현 후에는 요청의 memberId 대신 인증된 회원 ID를 사용할 예정.
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new CustomException(ErrorCode.THEATER_NOT_FOUND));

        StoreOrder order = StoreOrder.builder()
                .member(member)
                .theater(theater)
                .build();
        List<StoreOrderItem> items = new ArrayList<>();

        // 동시 구매 시 재고 행의 잠금 순서가 엇갈리지 않도록 상품 ID 오름차순으로 조회한다.
        for (StoreOrderReqDTO.OrderItemDTO item : request.items().stream()
                .sorted(Comparator.comparing(StoreOrderReqDTO.OrderItemDTO::productId)).toList()) {
            TheaterStock stock = theaterStockRepository
                    .findByTheaterIdAndProductIdForUpdate(theaterId, item.productId())
                    .orElseThrow(() -> new CustomException(productRepository.existsById(item.productId())
                            ? ErrorCode.THEATER_STOCK_NOT_FOUND : ErrorCode.PRODUCT_NOT_FOUND));
            stock.decreaseQuantity(item.quantity());
            items.add(StoreOrderItem.builder()
                    .order(order)
                    .product(stock.getProduct())
                    .quantity(item.quantity())
                    .build());
        }

        storeOrderRepository.save(order);
        return StoreOrderResDTO.from(order, storeOrderItemRepository.saveAll(items));
    }
}
