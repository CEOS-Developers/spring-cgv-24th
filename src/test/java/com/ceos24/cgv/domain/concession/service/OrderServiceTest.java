package com.ceos24.cgv.domain.concession.service;

import com.ceos24.cgv.domain.concession.dto.request.OrderCreateRequest;
import com.ceos24.cgv.domain.concession.dto.request.OrderItemRequest;
import com.ceos24.cgv.domain.concession.dto.response.OrderResponse;
import com.ceos24.cgv.domain.concession.entity.Order;
import com.ceos24.cgv.domain.concession.entity.OrderItem;
import com.ceos24.cgv.domain.concession.enums.OrderStatus;
import com.ceos24.cgv.domain.concession.exception.ConcessionErrorStatus;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import com.ceos24.cgv.domain.concession.repository.OrderItemRepository;
import com.ceos24.cgv.domain.concession.repository.OrderRepository;
import com.ceos24.cgv.domain.concession.repository.StockRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.user.exception.UserErrorStatus;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;

import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private OrderRepository orderRepository;
	@Mock
	private OrderItemRepository orderItemRepository;
	@Mock
	private StockRepository stockRepository;
	@Mock
	private ItemRepository itemRepository;
	@Mock
	private TheaterRepository theaterRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private PaymentRepository paymentRepository;

	@InjectMocks
	private OrderService orderService;

	@Test
	void 동일_상품_주문은_수량을_합산해_한_주문항목으로_생성한다() {
		var user = user(1L);
		var theater = theater(2L);
		var item = item(3L, "팝콘", 5_000);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(theaterRepository.findById(2L)).thenReturn(Optional.of(theater));
		when(itemRepository.findById(3L)).thenReturn(Optional.of(item));
		when(stockRepository.findByTheaterIdAndItemId(2L, 3L)).thenReturn(Optional.of(stock(1L, theater, item, 10)));
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));
		when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));

		Long result = orderService.create(1L, new OrderCreateRequest(2L, List.of(
				new OrderItemRequest(3L, 1), new OrderItemRequest(3L, 2)
		)));

		ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
		ArgumentCaptor<OrderItem> orderItemCaptor = ArgumentCaptor.forClass(OrderItem.class);
		verify(orderRepository).save(orderCaptor.capture());
		verify(orderItemRepository).save(orderItemCaptor.capture());
		assertThat(result).isEqualTo(10L);
		assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(15_000);
		assertThat(orderItemCaptor.getValue().getQuantity()).isEqualTo(3);
		assertThat(orderItemCaptor.getValue().getPrice()).isEqualTo(5_000);

		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
		verify(paymentRepository).save(paymentCaptor.capture());
		assertThat(paymentCaptor.getValue().getOrder()).isSameAs(orderCaptor.getValue());
		assertThat(paymentCaptor.getValue().getAmount()).isEqualTo(15_000);
		verify(stockRepository, never()).decreaseStock(anyLong(), anyLong(), anyInt());
	}

	@Test
	void 재고가_주문수량보다_적으면_주문을_생성하지_않는다() {
		var user = user(1L);
		var theater = theater(2L);
		var item = item(3L, "팝콘", 5_000);
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(theaterRepository.findById(2L)).thenReturn(Optional.of(theater));
		when(itemRepository.findById(3L)).thenReturn(Optional.of(item));
		when(stockRepository.findByTheaterIdAndItemId(2L, 3L)).thenReturn(Optional.of(stock(1L, theater, item, 1)));

		assertThatThrownBy(() -> orderService.create(1L, new OrderCreateRequest(2L, List.of(new OrderItemRequest(3L, 2)))))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(ConcessionErrorStatus.INSUFFICIENT_STOCK);
		verify(orderRepository, never()).save(any());
		verify(paymentRepository, never()).save(any());
		verify(orderItemRepository, never()).save(any());
	}

	@Test
	void 주문_상세를_본인만_조회할_수_있다() {
		var order = order(10L, user(1L), theater(2L), 5_000);
		var item = item(3L, "팝콘", 5_000);
		when(orderRepository.findByIdWithTheater(10L)).thenReturn(Optional.of(order));
		when(orderItemRepository.findByOrderIdWithItem(10L)).thenReturn(List.of(orderItem(20L, order, item, 1, 5_000)));

		OrderResponse result = orderService.findById(10L, 1L);

		assertThat(result.orderId()).isEqualTo(10L);
		assertThat(result.items()).hasSize(1);
		assertThat(result.totalPrice()).isEqualTo(5_000);
	}

	@Test
	void 결제_완료_처리는_itemId_오름차순으로_재고를_차감하고_주문을_결제완료로_바꾼다() {
		var theater = theater(2L);
		var order = order(10L, user(1L), theater, 15_000);
		var popcorn = item(3L, "팝콘", 5_000);
		var cola = item(5L, "콜라", 2_500);
		when(orderItemRepository.findByOrderIdWithItem(10L)).thenReturn(List.of(
				orderItem(21L, order, cola, 2, 2_500), orderItem(20L, order, popcorn, 1, 5_000)
		));
		when(stockRepository.decreaseStock(anyLong(), anyLong(), anyInt())).thenReturn(1);

		orderService.completePaidOrder(order);

		InOrder inOrder = inOrder(stockRepository);
		inOrder.verify(stockRepository).decreaseStock(2L, 3L, 1);
		inOrder.verify(stockRepository).decreaseStock(2L, 5L, 2);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void 차감할_재고가_하나라도_부족하면_재고부족_예외가_나고_결제완료로_바뀌지_않는다() {
		var order = order(10L, user(1L), theater(2L), 15_000);
		var popcorn = item(3L, "팝콘", 5_000);
		var cola = item(5L, "콜라", 2_500);
		when(orderItemRepository.findByOrderIdWithItem(10L)).thenReturn(List.of(
				orderItem(20L, order, popcorn, 1, 5_000), orderItem(21L, order, cola, 2, 2_500)
		));
		when(stockRepository.decreaseStock(2L, 3L, 1)).thenReturn(1);
		when(stockRepository.decreaseStock(2L, 5L, 2)).thenReturn(0);

		assertThatThrownBy(() -> orderService.completePaidOrder(order))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(ConcessionErrorStatus.INSUFFICIENT_STOCK);
		assertThat(order.getStatus()).isNotEqualTo(OrderStatus.PAID);
	}

	@Test
	void 존재하지_않는_사용자의_주문목록은_조회할_수_없다() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.findByUserId(999L))
				.isInstanceOf(GeneralException.class)
				.extracting(error -> ((GeneralException) error).getCode())
				.isEqualTo(UserErrorStatus.USER_NOT_FOUND);
	}
}
