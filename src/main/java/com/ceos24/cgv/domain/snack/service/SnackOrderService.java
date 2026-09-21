package com.ceos24.cgv.domain.snack.service;

import com.ceos24.cgv.domain.snack.dto.request.SnackOrderCreateRequest;
import com.ceos24.cgv.domain.snack.dto.request.SnackOrderItemRequest;
import com.ceos24.cgv.domain.snack.dto.response.SnackOrderResponse;
import com.ceos24.cgv.domain.snack.entity.SnackOrder;
import com.ceos24.cgv.domain.snack.entity.SnackOrderItem;
import com.ceos24.cgv.domain.snack.entity.SnackStock;
import com.ceos24.cgv.domain.snack.exception.SnackOrderErrorCode;
import com.ceos24.cgv.domain.snack.exception.SnackOrderException;
import com.ceos24.cgv.domain.snack.repository.SnackOrderRepository;
import com.ceos24.cgv.domain.snack.repository.SnackStockRepository;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorCode;
import com.ceos24.cgv.domain.theater.exception.TheaterException;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.exception.UserErrorCode;
import com.ceos24.cgv.domain.user.exception.UserException;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SnackOrderService {

    private final SnackOrderRepository snackOrderRepository;
    private final SnackStockRepository snackStockRepository;
    private final UserRepository userRepository;
    private final TheaterRepository theaterRepository;

    /**
     * 매점 상품 구매
     */
    @Transactional
    public SnackOrderResponse purchaseSnacks(
            Long userId,
            Long theaterId,
            SnackOrderCreateRequest request
    ) {
        validateOrderRequest(request);

        User user = getUser(userId);
        Theater theater = getTheater(theaterId);

        List<Long> snackItemIds = request.items()
                .stream()
                .map(SnackOrderItemRequest::snackItemId)
                .toList();

        List<SnackStock> stocks =
                snackStockRepository
                        .findAllByTheaterIdAndSnackItemIdsForUpdate(
                                theaterId,
                                snackItemIds
                        );

        validateAllStocksExist(
                snackItemIds,
                stocks
        );

        Map<Long, SnackStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(
                        stock -> stock.getSnackItem().getId(),
                        Function.identity()
                ));

        SnackOrder snackOrder =
                SnackOrder.create(user, theater);

        for (SnackOrderItemRequest itemRequest
                : request.items()) {

            SnackStock stock =
                    stockMap.get(itemRequest.snackItemId());

            validateSufficientStock(
                    stock,
                    itemRequest.quantity()
            );

            stock.decreaseStock(
                    itemRequest.quantity()
            );

            SnackOrderItem orderItem =
                    SnackOrderItem.create(
                            stock.getSnackItem(),
                            itemRequest.quantity()
                    );

            snackOrder.addOrderItem(orderItem);
        }

        snackOrder.complete();

        SnackOrder savedOrder =
                snackOrderRepository.save(snackOrder);

        return SnackOrderResponse.from(savedOrder);
    }

    private void validateOrderRequest(
            SnackOrderCreateRequest request
    ) {
        if (request == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new SnackOrderException(
                    SnackOrderErrorCode.EMPTY_SNACK_ORDER
            );
        }

        Set<Long> snackItemIds = new HashSet<>();

        for (SnackOrderItemRequest item
                : request.items()) {

            if (item == null
                    || item.snackItemId() == null) {
                throw new SnackOrderException(
                        SnackOrderErrorCode.INVALID_SNACK_ITEM
                );
            }

            if (item.quantity() == null
                    || item.quantity() <= 0) {
                throw new SnackOrderException(
                        SnackOrderErrorCode
                                .INVALID_SNACK_QUANTITY
                );
            }

            if (!snackItemIds.add(item.snackItemId())) {
                throw new SnackOrderException(
                        SnackOrderErrorCode
                                .DUPLICATE_SNACK_ITEM
                );
            }
        }
    }

    private void validateAllStocksExist(
            List<Long> snackItemIds,
            List<SnackStock> stocks
    ) {
        if (stocks.size() != snackItemIds.size()) {
            throw new SnackOrderException(
                    SnackOrderErrorCode
                            .SNACK_STOCK_NOT_FOUND
            );
        }
    }

    private void validateSufficientStock(
            SnackStock stock,
            Integer purchaseQuantity
    ) {
        if (stock.getStockQuantity()
                < purchaseQuantity) {
            throw new SnackOrderException(
                    SnackOrderErrorCode
                            .INSUFFICIENT_SNACK_STOCK
            );
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );
    }

    private Theater getTheater(Long theaterId) {
        return theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new TheaterException(
                                TheaterErrorCode.THEATER_NOT_FOUND
                        )
                );
    }
}