package com.cgvclone.cgv.domain.cinema.dto;

import com.cgvclone.cgv.domain.goods.Goods;
import com.cgvclone.cgv.domain.stock.Stock;
import java.util.List;

public record CinemaGoodsListResponse(List<Item> goods) {

    public static CinemaGoodsListResponse from(List<Stock> stocks) {
        return new CinemaGoodsListResponse(stocks.stream().map(Item::from).toList());
    }

    public record Item(
            Long goodsId,
            String name,
            Integer price,
            String description,
            String goodsImageUrl,
            Integer quantity
    ) {

        private static Item from(Stock stock) {
            Goods goods = stock.getGoods();
            return new Item(
                    goods.getGoodsId(),
                    goods.getName(),
                    goods.getPrice(),
                    goods.getDescription(),
                    goods.getGoodsImageUrl(),
                    stock.getQuantity()
            );
        }
    }
}
