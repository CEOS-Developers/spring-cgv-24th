package com.cgvclone.cgv.domain.stock;

import com.cgvclone.cgv.domain.goods.Goods;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("select s.goods from Stock s where s.cinema.cinemaId = :cinemaId and s.goods.goodsId = :goodsId")
    Optional<Goods> findGoodsForSale(@Param("cinemaId") Long cinemaId, @Param("goodsId") Long goodsId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Stock s set s.quantity = s.quantity - :quantity
            where s.cinema.cinemaId = :cinemaId and s.goods.goodsId = :goodsId
              and :quantity > 0 and s.quantity >= :quantity
            """)
    int decreaseIfAvailable(@Param("cinemaId") Long cinemaId, @Param("goodsId") Long goodsId,
                            @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Stock s set s.quantity = s.quantity + :quantity
            where s.cinema.cinemaId = :cinemaId and s.goods.goodsId = :goodsId
              and :quantity > 0 and s.quantity <= 2147483647 - :quantity
            """)
    int restoreQuantity(@Param("cinemaId") Long cinemaId, @Param("goodsId") Long goodsId,
                        @Param("quantity") int quantity);
}
