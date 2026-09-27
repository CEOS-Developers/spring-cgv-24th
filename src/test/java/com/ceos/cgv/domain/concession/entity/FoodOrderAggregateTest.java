package com.ceos.cgv.domain.concession.entity;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FoodOrderAggregateTest {
    @Test
    void 외부에서_주문_항목을_직접_추가할_수_없다() {
        FoodOrder order = create(new Product("상품", 100L, "설명"), 1);
        assertThatThrownBy(() -> order.getItems().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 주문_항목의_단가와_총액은_상품_가격이_바뀌어도_유지된다() {
        Product product = new Product("상품", 1200L, "설명");
        FoodOrder order = create(product, 2);
        ReflectionTestUtils.setField(product, "price", 3000L);

        assertThat(order.getTotalPrice()).isEqualTo(2400L);
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getFoodOrder()).isSameAs(order);
            assertThat(item.getUnitPrice()).isEqualTo(1200L);
            assertThat(item.subtotal()).isEqualTo(2400L);
        });
    }

    @Test
    void 잘못된_수량과_총액_오버플로는_주문_생성을_거절한다() {
        Product product = new Product("상품", Long.MAX_VALUE, "설명");
        assertThatThrownBy(() -> create(product, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> create(product, 2)).isInstanceOf(ArithmeticException.class);
    }

    private FoodOrder create(Product product, int quantity) {
        return FoodOrder.create(mock(User.class), mock(Cinema.class),
                List.of(new FoodOrder.ItemSelection(product, quantity)));
    }
}
