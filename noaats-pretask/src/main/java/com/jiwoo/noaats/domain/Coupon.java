package com.jiwoo.noaats.domain;

import lombok.Data;

@Data
public class Coupon {
    private String name;

    private String type;      // fixed | percent
    private Double value;     // 원 or %
    private Integer minSpend; // 최소 구매금액(원). 없으면 0

    // 정률 쿠폰의 최대 할인 한도(선택). 없으면 0
    private Integer maxDiscount;
}
