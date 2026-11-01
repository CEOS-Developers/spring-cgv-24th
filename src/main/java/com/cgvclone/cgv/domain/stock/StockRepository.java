package com.cgvclone.cgv.domain.stock;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByCinema_CinemaIdAndGoods_GoodsId(Long cinemaId, Long goodsId);
}
