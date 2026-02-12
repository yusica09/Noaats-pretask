package com.jiwoo.noaats.form;

import com.jiwoo.noaats.domain.Coupon;
import com.jiwoo.noaats.domain.ProductItem;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DiscountForm {
    // 상품(최대 5개)
    private List<ProductItem> products = new ArrayList<>();

    // 보유 쿠폰(최대 3개)
    private List<Coupon> coupons = new ArrayList<>();

    // 프로모션: 금액대 할인(주문 단위)
    private Integer thresholdAmount; // 조건 금액
    private Integer thresholdOff;    // 할인액

    // 프로모션 적용 순서
    // true: 금액대 할인 → 쿠폰
    // false: 쿠폰 → 금액대 할인
    private Boolean promoBeforeCoupon;

    // 배송 정책
    private Integer shippingFee;
    private Integer freeShippingThreshold;
}
