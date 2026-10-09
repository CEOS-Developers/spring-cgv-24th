package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.repository.BranchRepository;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentOrderResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.repository.PaymentCancelRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest.Item;
import com.ceos24.cgv.domain.store.dto.PurchaseOrderResponse;
import com.ceos24.cgv.domain.store.dto.PurchasePaymentAttempt;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseProduct;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.store.repository.ProductRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.store.repository.StockRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final Clock clock;

    // 주문과 결제 주문을 함께 만든다. 재고는 차감하지 않는다. 결제창을 닫고 떠난 주문이 재고를 붙잡지 않게
    // 하려면 만료와 복구 장치가 필요한데, 재고는 아무 단위나 대체할 수 있어 미리 잡아 둘 이유가 없다.
    @Transactional
    public PurchaseOrderResponse createOrder(Long userId, PurchaseCreateRequest req) {
        validateNoDuplicateProducts(req.items());
        User user = userRepository.getByIdOrThrow(userId);
        Branch branch = getOperatingBranch(req.branchId());
        Map<Long, Product> products = findProducts(req.items());
        ensureAvailable(branch.getId(), req.items());

        Purchase purchase = Purchase.builder().user(user).branch(branch).build();
        // 요청 순서와 무관하게 항목 순서가 일정하도록 상품 id 순으로 담는다
        req.items().stream()
                .sorted(Comparator.comparing(Item::productId))
                .forEach(item -> purchase.addItem(products.get(item.productId()), item.quantity()));
        purchaseRepository.save(purchase);
        Payment payment = paymentRepository.save(Payment.builder().purchase(purchase).build());

        return new PurchaseOrderResponse(PurchaseResponse.from(purchase),
                PaymentOrderResponse.of(payment, orderNameOf(purchase)));
    }

    // 승인 T1. 재고를 여기서 차감한다. 품절이면 토스를 부르지 않으므로 돈이 빠지지 않고,
    // 토스 승인 뒤에 차감하면 품절일 때 이미 빠진 돈을 환불해야 한다. 재고 락은 이 트랜잭션 커밋과 함께 풀려
    // 토스를 기다리는 동안 같은 상품의 다른 주문을 막지 않는다.
    // 어느 단계에서 예외가 나도 차감이 전부 롤백되어 부분 성공이 없다.
    @Transactional
    public PurchasePaymentAttempt startPayment(Long id, Long userId, PaymentConfirmRequest req) {
        Purchase purchase = findOwnedForUpdate(id, userId);
        Payment payment = paymentRepository.findByOrderIdAndPurchaseId(req.orderId(), id)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.verifyAmount(req.amount());
        purchase.verifyPayable();
        // 주문 뒤에 휴관했으면 팔지 않는다
        getOperatingBranch(purchase.getBranch().getId());
        // 차감보다 먼저 한다. 이미 승인 중인 주문의 중복 요청이 재고 행을 잠갔다가 롤백하는 일이 없다.
        // 차감이 실패하면 이 상태 변경도 함께 롤백된다.
        payment.startApproval();

        deductStocks(purchase);

        return new PurchasePaymentAttempt(payment.getId(), id, req.paymentKey(),
                payment.getOrderId(), payment.getAmount());
    }

    // 승인 T2 (성공). 토스는 이미 승인했으므로 여기서 실패하면 돈은 빠졌는데 구매가 확정되지 않는다.
    // 롤백되면 결제가 IN_PROGRESS로 남아, 결제 조회로 실제 상태를 맞출 단서가 된다.
    @Transactional
    public PurchaseResponse completePayment(PurchasePaymentAttempt attempt, TossPayment approved) {
        LocalDateTime now = LocalDateTime.now(clock);
        Payment payment = findPayment(attempt.paymentId());
        Purchase purchase = purchaseRepository.findWithDetails(attempt.purchaseId())
                .orElseThrow(() -> new CustomException(ErrorCode.PURCHASE_NOT_FOUND));

        payment.approve(approved.paymentKey(), approved.method(), approved.approvedAtIn(clock.getZone(), now));
        purchase.complete(now);
        return PurchaseResponse.from(purchase);
    }

    // 승인 T2 (거절). T1이 차감한 재고를 되돌린다. 예매와 달리 붙잡아 둘 것이 없어 주문도 실패로 끝낸다.
    @Transactional
    public void failPayment(PurchasePaymentAttempt attempt, String failureCode, String failureMessage) {
        Payment payment = findPayment(attempt.paymentId());
        Purchase purchase = payment.getPurchase();

        payment.fail(failureCode, failureMessage);
        purchase.fail();
        restoreStocks(purchase);
    }

    // 구매 하나에 결제 하나라 결제창만 열고 떠난 주문(READY)도 그대로 보여준다. 그 주문의 현재 상태가 그것이다.
    public PaymentResponse findPayment(Long id, Long userId) {
        if (!purchaseRepository.existsByIdAndUserId(id, userId)) {
            throw new CustomException(ErrorCode.PURCHASE_NOT_FOUND);
        }
        Payment payment = paymentRepository.findByPurchaseId(id)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentResponse.of(payment, paymentCancelRepository.findByPaymentIdInOrderByIdAsc(List.of(payment.getId())));
    }

    // 없는 사용자를 빈 목록으로 돌려주면 잘못된 id가 "구매 내역 없음"으로 숨는다.
    public List<PurchaseResponse> findHistory(Long userId) {
        userRepository.validateExists(userId);
        return PurchaseResponse.listOf(purchaseRepository.findHistoryRowsByUserId(userId, PurchaseStatus.PAID));
    }

    private void validateNoDuplicateProducts(List<Item> items) {
        if (items.stream().map(Item::productId).distinct().count() != items.size()) {
            throw new CustomException(ErrorCode.DUPLICATE_PRODUCT_IN_REQUEST);
        }
    }

    private Branch getOperatingBranch(Long branchId) {
        Branch branch = branchRepository.getByIdOrThrow(branchId);
        if (!branch.isOperating()) {
            throw new CustomException(ErrorCode.BRANCH_NOT_OPERATING);
        }
        return branch;
    }

    private Map<Long, Product> findProducts(List<Item> items) {
        List<Long> productIds = items.stream().map(Item::productId).toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != productIds.size()) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return products;
    }

    // 지금 팔 수 없는 주문은 결제창을 열기 전에 거른다. 잠그지 않으므로 통과해도 승인 직전에 품절일 수 있다.
    private void ensureAvailable(Long branchId, List<Item> items) {
        List<Long> productIds = items.stream().map(Item::productId).toList();
        Map<Long, Stock> stocks = stockRepository.findByBranchIdAndProductIdIn(branchId, productIds).stream()
                .collect(Collectors.toMap(stock -> stock.getProduct().getId(), Function.identity()));
        for (Item item : items) {
            Stock stock = stocks.get(item.productId());
            // 재고 행이 없으면 그 극장에서 팔지 않는 상품이다. 고객에게는 품절과 같다
            if (stock == null || item.quantity() > stock.availableQuantity()) {
                throw new CustomException(ErrorCode.OUT_OF_STOCK);
            }
        }
    }

    // 남의 주문도 PURCHASE_NOT_FOUND다. 예매와 같이 순차 id로 다른 회원의 주문이 있는지 알아낼 수 없게 한다.
    private Purchase findOwnedForUpdate(Long id, Long userId) {
        return purchaseRepository.findOwnedForUpdate(id, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PURCHASE_NOT_FOUND));
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void deductStocks(Purchase purchase) {
        forEachStockInLockOrder(purchase, (stock, item) -> stock.decrease(item.getQuantity()));
    }

    private void restoreStocks(Purchase purchase) {
        forEachStockInLockOrder(purchase, (stock, item) -> stock.restore(item.getQuantity()));
    }

    // 상품 id 오름차순으로 한 건씩 잠가 획득 순서를 코드로 고정한다.
    // [A,B]와 [B,A] 주문이 서로를 물고 도는 데드락이 여기서 사라진다. 차감과 복구가 같은 순서를 쓴다.
    private void forEachStockInLockOrder(Purchase purchase, BiConsumer<Stock, PurchaseProduct> action) {
        Long branchId = purchase.getBranch().getId();
        List<PurchaseProduct> lockOrder = purchase.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        try {
            for (PurchaseProduct item : lockOrder) {
                Stock stock = stockRepository.findByBranchIdAndProductIdForUpdate(branchId, item.getProduct().getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.OUT_OF_STOCK));
                action.accept(stock, item);
            }
        } catch (ConcurrencyFailureException e) {
            // 락 대기 타임아웃. 품절이라는 뜻이 아니라 판정하지 못했다는 뜻이라 재시도를 안내한다.
            throw new CustomException(ErrorCode.STOCK_LOCK_CONFLICT);
        }
    }

    // 결제위젯과 토스 결제 내역에 보이는 이름이다.
    private String orderNameOf(Purchase purchase) {
        List<PurchaseProduct> items = purchase.getItems();
        String first = items.getFirst().getProduct().getName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }
}
