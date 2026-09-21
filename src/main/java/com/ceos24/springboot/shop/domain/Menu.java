package com.ceos24.springboot.shop.domain;

import com.ceos24.springboot.theater.domain.Theater;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "menu")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {
//  모든 영화관의 매점 메뉴는 같음.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "menu_name")
    private String menuName;

    @Column(name = "menu_price")
    private Integer menuPrice;


}