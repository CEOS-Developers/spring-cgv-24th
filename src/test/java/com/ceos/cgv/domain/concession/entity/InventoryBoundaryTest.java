package com.ceos.cgv.domain.concession.entity;

import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryBoundaryTest {
    @Test
    void 차감량이_양수가_아니면_재고를_변경하지_않는다() {
        Inventory inventory = new Inventory(null, null, 5);
        for (Integer quantity : new Integer[]{0, -2, null}) {
            assertThatThrownBy(() -> inventory.decrease(quantity))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REQUEST);
            assertThat(inventory.getStockQuantity()).isEqualTo(5);
        }
        inventory.decrease(5);
        assertThat(inventory.getStockQuantity()).isZero();
    }
}
