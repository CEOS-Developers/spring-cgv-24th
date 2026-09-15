package com.ceos24.springboot.favorite.repository;

import com.ceos24.springboot.favorite.domain.TheaterFavorites;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheaterFavoritesRepository extends JpaRepository<TheaterFavorites, Long> {

}
