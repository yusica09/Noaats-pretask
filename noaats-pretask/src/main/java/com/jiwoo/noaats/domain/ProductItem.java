package com.jiwoo.noaats.domain;

import lombok.Data;

@Data
public class ProductItem {
    private String name;
    private Integer price;    // 단가
    private Integer quantity; // 수량
    private boolean active = true; // 상품 칸 활성화 확인

    // 상품 자체 할인(선택)
    private String discountType; // none | fixed | percent
    private Double discountValue; 
    // fixed면 원(상품 전체 라인에 적용), percent면 %
    
}
