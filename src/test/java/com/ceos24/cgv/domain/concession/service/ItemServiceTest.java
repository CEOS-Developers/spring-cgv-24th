package com.ceos24.cgv.domain.concession.service;

import com.ceos24.cgv.domain.concession.dto.request.ItemCreateRequest;
import com.ceos24.cgv.domain.concession.dto.response.ItemResponse;
import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.ceos24.cgv.support.TestFixtures.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void 매점_상품을_생성하고_ID를_반환한다() {
        when(itemRepository.save(any(Item.class))).thenReturn(item(1L, "팝콘", 6_000));

        Long result = itemService.create(new ItemCreateRequest("팝콘", 6_000));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(captor.capture());
        assertThat(result).isEqualTo(1L);
        assertThat(captor.getValue().getName()).isEqualTo("팝콘");
        assertThat(captor.getValue().getPrice()).isEqualTo(6_000);
    }

    @Test
    void 매점_상품_목록을_응답으로_변환한다() {
        when(itemRepository.findAll()).thenReturn(List.of(item(1L, "팝콘", 6_000), item(2L, "콜라", 3_000)));

        List<ItemResponse> result = itemService.findAll();

        assertThat(result).containsExactly(
                new ItemResponse(1L, "팝콘", 6_000),
                new ItemResponse(2L, "콜라", 3_000)
        );
    }
}
