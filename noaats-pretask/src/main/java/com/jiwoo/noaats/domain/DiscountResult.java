package com.jiwoo.noaats.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiscountResult {
    private long baseMerchandise; // 상품 합계(할인 전)
    private long baseShipping;    // 기준 배송비(무료배송 반영: 0 또는 배송비)
    private long basePay;         // 기준 결제금액(상품+배송)
    
    private long finalPay;        // 배송 포함 최종 결제
    private long totalSaved;      // (기준 - 최종) 절감액
    private double savedRate;     // 절감률(%)

    private String summary;       // 사람이 읽는 결과 요약
    private String detail;        // 계산 과정/플랜(검증용)
}
