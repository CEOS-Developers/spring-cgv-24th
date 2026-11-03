package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.store.dto.request.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.request.PurchaseItemRequest;
import com.ceos24.cgv.domain.store.dto.response.ProductStockResponse;
import com.ceos24.cgv.domain.store.dto.response.PurchaseCreateResponse;
import com.ceos24.cgv.domain.store.dto.response.PurchaseResponse;
import com.ceos24.cgv.domain.store.entity.CinemaStock;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseItem;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseItemRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
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
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;

    // 상품·재고 조회
    public List<ProductStockResponse> getProductsByCinema(Long cinemaId) {
        if (!cinemaRepository.existsByIdAndActiveTrue(cinemaId)) {
            throw new BusinessException(ErrorCode.CINEMA_NOT_FOUND);
        }

        return cinemaStockRepository.findAllByCinemaIdOrderByProductIdAsc(cinemaId)
                .stream()
                .map(ProductStockResponse::from)
                .toList();
    }

    // 결제 대기 주문 생성 및 재고 확보
    @Transactional
    public PurchaseCreateResponse createPurchase(
            String username,
            PurchaseCreateRequest request
    ) {
        // 사용자 조회
        UserEntity userEntity = findActiveLocalUser(username);

        // 영화관 조회
        Cinema cinema = cinemaRepository.findByIdAndActiveTrue(request.cinemaId()).
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

        long totalAmount = calculateTotalAmount(request.items(), stockByProductId);

        Purchase purchase = purchaseRepository.save(
                Purchase.create(userEntity, cinema, totalAmount)
        );

        // 재고 차감과 PurchaseItem 생성
        List<PurchaseItem> purchaseItems = new ArrayList<>();

        for (PurchaseItemRequest item : request.items()) {
            CinemaStock stock = stockByProductId.get(item.productId());

            // 결제 전에 재고를 확보한다. 승인 시에는 다시 차감하지 않는다.
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
        paymentRepository.save(Payment.create(purchase));

        return PurchaseCreateResponse.from(purchase);
    }

    private long calculateTotalAmount(
            List<PurchaseItemRequest> items,
            Map<Long, CinemaStock> stockByProductId
    ) {
        long totalAmount = 0;

        try {
            for (PurchaseItemRequest item : items) {
                int unitPrice = stockByProductId.get(item.productId()).getProduct().getPrice();
                long itemAmount = Math.multiplyExact((long) unitPrice, item.quantity());
                totalAmount = Math.addExact(totalAmount, itemAmount);
            }
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_PURCHASE_AMOUNT);
        }

        if (totalAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PURCHASE_AMOUNT);
        }

        return totalAmount;
    }

    // 사용자의 주문 목록 조회
    public List<PurchaseResponse> getPurchases(String username) {
        UserEntity userEntity = findActiveLocalUser(username);

        return purchaseRepository
                .findAllByUserEntity_IdOrderByCreatedAtDesc(userEntity.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 사용자의 주문 단건 조회
    public PurchaseResponse getPurchase(String username, Long purchaseId) {
        UserEntity userEntity = findActiveLocalUser(username);

        return toResponse(getOwnedPurchase(userEntity.getId(), purchaseId));
    }

    private Purchase getOwnedPurchase(Long authenticatedUserId, Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_FOUND));

        if (!purchase.getUserEntity().getId().equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.PURCHASE_ACCESS_DENIED);
        }

        return purchase;
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        List<PurchaseItem> purchaseItems = purchaseItemRepository
                .findAllByPurchaseIdOrderByIdAsc(purchase.getId());

        return PurchaseResponse.from(purchase, purchaseItems);
    }

    private UserEntity findActiveLocalUser(String username) {
        return userRepository
                .findByUsernameAndIsLockAndIsSocial(
                        username,
                        false,
                        false
                )
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );
    }
}
