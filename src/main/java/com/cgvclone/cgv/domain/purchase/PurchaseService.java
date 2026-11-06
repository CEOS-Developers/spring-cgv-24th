package com.cgvclone.cgv.domain.purchase;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.cinema.Cinema;
import com.cgvclone.cgv.domain.cinema.CinemaService;
import com.cgvclone.cgv.domain.goods.Goods;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseCreateRequest;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseItemRequest;
import com.cgvclone.cgv.domain.purchase.dto.PurchaseResponse;
import com.cgvclone.cgv.domain.stock.StockRepository;
import com.cgvclone.cgv.domain.user.User;
import com.cgvclone.cgv.domain.user.UserService;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final StockRepository stockRepository;
    private final UserService userService;
    private final CinemaService cinemaService;

    @Transactional
    public PurchaseResponse createPurchase(Long userId, PurchaseCreateRequest request) {
        Map<Long, Integer> quantities = validate(request);
        User user = userService.getUser(userId);
        Cinema cinema = cinemaService.getCinemaEntity(request.cinemaId());
        Map<Goods, Integer> goodsQuantities = new HashMap<>();
        quantities.forEach((goodsId, quantity) -> {
            Goods goods = stockRepository.findGoodsForSale(request.cinemaId(), goodsId)
                    .orElseThrow(() -> new GlobalException(ErrorCode.STOCK_NOT_FOUND));
            goodsQuantities.put(goods, quantity);
        });

        Purchase purchase;
        try {
            purchase = Purchase.builder()
                    .user(user)
                    .cinema(cinema)
                    .goodsQuantities(goodsQuantities)
                    .build();
        } catch (ArithmeticException exception) {
            throw new GlobalException(ErrorCode.PURCHASE_AMOUNT_EXCEEDED);
        }
        if (purchase.getTotalPrice() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_PURCHASE_REQUEST);
        }
        // 결제가 진행되는 동안 재고를 예약한다. 결제 전 주문 취소 시 반환한다.
        quantities.forEach((goodsId, quantity) -> {
            if (stockRepository.decreaseIfAvailable(request.cinemaId(), goodsId, quantity) != 1) {
                throw new GlobalException(ErrorCode.INSUFFICIENT_STOCK);
            }
        });
        Purchase savedPurchase = purchaseRepository.save(purchase);
        return PurchaseResponse.from(savedPurchase);
    }

    private Map<Long, Integer> validate(PurchaseCreateRequest request) {
        if (request == null || request.cinemaId() == null || request.cinemaId() <= 0 || request.items() == null
                || request.items().isEmpty()) {
            throw new GlobalException(ErrorCode.INVALID_PURCHASE_REQUEST);
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (PurchaseItemRequest item : request.items()) {
            if (item == null || item.goodsId() == null || item.goodsId() <= 0
                    || item.quantity() == null || item.quantity() <= 0
                    || quantities.putIfAbsent(item.goodsId(), item.quantity()) != null) {
                throw new GlobalException(ErrorCode.INVALID_PURCHASE_REQUEST);
            }
        }
        return quantities;
    }
}
