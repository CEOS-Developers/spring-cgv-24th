package com.ceos24.cgv.domain.concession.service;

import com.ceos24.cgv.domain.concession.dto.request.ItemCreateRequest;
import com.ceos24.cgv.domain.concession.dto.response.ItemResponse;
import com.ceos24.cgv.domain.concession.entity.Item;
import com.ceos24.cgv.domain.concession.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional
    public Long create(ItemCreateRequest request) {
        Item item = Item.create(request.name(), request.price());
        return itemRepository.save(item).getId();
    }

    public List<ItemResponse> findAll() {
        return itemRepository.findAll().stream()
                .map(ItemResponse::from)
                .toList();
    }
}
