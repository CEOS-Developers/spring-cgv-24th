package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.store.dto.request.CreateMenuRequest;
import com.ceos24.cgv.domain.store.entity.Menu;
import com.ceos24.cgv.domain.store.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MenuAdminService {

    private final MenuRepository menuRepository;

    @Transactional
    public void createMenu(CreateMenuRequest request) {
        Menu menu = new Menu(request.name(), request.price());
        menuRepository.save(menu);
    }
}
