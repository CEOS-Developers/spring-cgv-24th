package com.ceos24.cgv.domain.store.entity;

import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    void 수량_1_미만으로는_만들_수_없다() {
        assertThatThrownBy(() -> stock(0))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY);
    }

    @Test
    void 최소_재고_1개를_남기고는_차감된다() {
        Stock stock = stock(5);

        stock.decrease(4);

        assertThat(stock.getQuantity()).isEqualTo(1);
        assertThat(stock.isSoldOut()).isTrue();
    }

    @Test
    void 차감_후_1_미만이_되면_거부하고_수량은_그대로다() {
        Stock stock = stock(5);

        assertThatThrownBy(() -> stock.decrease(5))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(stock.getQuantity()).isEqualTo(5);
    }

    @Test
    void 영_이하_차감은_거부한다() {
        Stock stock = stock(5);

        assertThatThrownBy(() -> stock.decrease(-1))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(stock.getQuantity()).isEqualTo(5);
    }

    @Test
    void 판매_가능_수량은_재고보다_하나_적다() {
        assertThat(stock(10).availableQuantity()).isEqualTo(9);
        assertThat(stock(10).isSoldOut()).isFalse();
        assertThat(stock(1).availableQuantity()).isZero();
    }

    private Stock stock(int quantity) {
        return TestFixtures.stock(null, TestFixtures.product("팝콘", 5000), quantity);
    }
}
