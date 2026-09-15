package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.store.dto.request.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.request.PurchaseItemRequest;
import com.ceos24.cgv.domain.store.dto.response.ProductStockResponse;
import com.ceos24.cgv.domain.store.dto.response.PurchaseResponse;
import com.ceos24.cgv.domain.store.entity.CinemaStock;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseItemRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PurchaseService {

    private final CinemaStockRepository cinemaStockRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;

    // 상품·재고 조회
    public List<ProductStockResponse> getProductsByCinema(Long cinemaId) {
        if (!cinemaRepository.existsById(cinemaId)) {
            throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
        }

        return cinemaStockRepository.findAllByCinemaIdOrderByProductIdAsc(cinemaId)
                .stream()
                .map(ProductStockResponse::from)
                .toList();
    }

    // 구매 등록
    @Transactional
    public Long createPurchase(
            Long userId,
            PurchaseCreateRequest request
    ) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 영화관 조회
        Cinema cinema = cinemaRepository.findById(request.cinemaId()).
                orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));

        // 요청에서 상품 ID 목록 추출
        List<Long> productIds = request.items().stream()
                .map(PurchaseItemRequest::productId)
                .toList();

        // 중복 상품 검사
        List<Long> distinctProductIds = productIds.stream()
                .distinct()
                .sorted()
                .toList();

        if (productIds.size() != distinctProductIds.size()) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_PRODUCT_REQUEST
            );
        }

        // 영화관 재고 잠금 조회
        List<CinemaStock> stocks =
                cinemaStockRepository
                        .findAllByCinemaIdAndProductIdsForUpdate(
                                cinema.getId(),
                                distinctProductIds
                        );

        // 해당 영화관에 모든 상품이 있는지 검사
        if (stocks.size() != distinctProductIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE_AT_CINEMA);
        }

        // 상품 ID를 기준으로 재고 Map 만들기
        Map<Long, CinemaStock> stockByProductId =
                stocks.stream()
                        .collect(Collectors.toMap(
                                stock -> stock.getProduct().getId(),
                                stock -> stock
                        ));

        // 모든 상품의 재고 수량 검사
        for (PurchaseItemRequest item : request.items()) {
            CinemaStock stock = stockByProductId.get(item.productId());

            if (stock.getQuantity() < item.quantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }

        Purchase purchase = purchaseRepository.save(
                Purchase.create(user, cinema)
        );

        // 재고 차감과 PurchaseItem 생성
        List<PurchaseItem> purchaseItems = new ArrayList<>();

        for (PurchaseItemRequest item : request.items()) {
            CinemaStock stock = stockByProductId.get(item.productId());

            // 구매 수량만큼 재고 차감
            stock.decrease(item.quantity());

            // 구매 상품 생성
            PurchaseItem purchaseItem = PurchaseItem.create(
                    stock.getProduct(),
                    purchase,
                    item.quantity()
            );

            purchaseItems.add(purchaseItem);
        }

        purchaseItemRepository.saveAll(purchaseItems);

        return purchase.getId();
    }

    // 사용자의 구매 목록 조회
    public List<PurchaseResponse> getPurchases(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return purchaseRepository.findAllByUserIdOrderByPurchasedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 사용자의 구매 단건 조회
    public PurchaseResponse getPurchase(Long userId, Long purchaseId) {
        return toResponse(getOwnedPurchase(userId, purchaseId));
    }

    private Purchase getOwnedPurchase(Long userId, Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_FOUND));

        if (!purchase.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PURCHASE_ACCESS_DENIED);
        }

        return purchase;
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        List<PurchaseItem> purchaseItems = purchaseItemRepository
                .findAllByPurchaseIdOrderByIdAsc(purchase.getId());

        return PurchaseResponse.from(purchase, purchaseItems);
    }
}
