package com.jiwoo.noaats.service;

import com.jiwoo.noaats.domain.Coupon;

import com.jiwoo.noaats.domain.DiscountResult;
import com.jiwoo.noaats.domain.ProductItem;
import com.jiwoo.noaats.form.DiscountForm;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DiscountCalculatorService {

    public DiscountResult calculateBestStrategy(DiscountForm form) {
        List<ProductItem> products = safeProducts(form.getProducts());
        List<Coupon> coupons = safeCoupons(form.getCoupons());

        boolean promoBeforeCoupon = form.getPromoBeforeCoupon() == null || form.getPromoBeforeCoupon();
        int shippingFee = nz(form.getShippingFee());
        int freeShip = nz(form.getFreeShippingThreshold());

        int thAmount = nz(form.getThresholdAmount());
        int thOff = nz(form.getThresholdOff());

        long baseMerch = productsBase(products);
        long baselinePay = baseMerch + shippingFee; // “아무 혜택 없고 배송비도 그냥 내는” 기준(단순 기준)

        // 1) 한 번에 구매(1주문): 쿠폰 0~1개 중 최적
        OrderCalc bestSingle = bestForOneOrder(products, coupons, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip);

        // 2) 두 번으로 나눠 구매(2주문): 상품 라인 단위 분할(최대 5개 → 32개)
        SplitCalc bestSplit = bestForTwoOrders(products, coupons, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip);

        // 최종 전략 선택
        boolean splitIsBetter = bestSplit.totalPay < bestSingle.totalPay;
        long finalPay = splitIsBetter ? bestSplit.totalPay : bestSingle.totalPay;

        long saved = baselinePay - finalPay;
        double rate = baselinePay == 0 ? 0 : saved * 100.0 / baselinePay;

        String summary = splitIsBetter
                ? String.format("✅ 추천: 2번으로 나눠 구매 (%,d원 절감)", saved)
                : String.format("✅ 추천: 1번에 구매 (%,d원 절감)", saved);

        String detail = buildDetail(products, coupons, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip,
                baselinePay, bestSingle, bestSplit, splitIsBetter);

        return DiscountResult.builder()
                .baseMerchandise(baseMerch)
                .finalPay(finalPay)
                .totalSaved(saved)
                .savedRate(round2(rate))
                .summary(summary)
                .detail(detail)
                .build();
    }

    /* ------------------------
       핵심 계산: 주문 1건 최적
       ------------------------ */
    private OrderCalc bestForOneOrder(List<ProductItem> products, List<Coupon> coupons,
                                      int thAmount, int thOff, boolean promoBeforeCoupon,
                                      int shippingFee, int freeShip) {

        List<Coupon> options = new ArrayList<>();
        options.add(null); // 쿠폰 미사용
        options.addAll(coupons);

        OrderCalc best = null;
        for (Coupon c : options) {
            OrderCalc calc = calcOrder(products, c, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip);
            if (best == null || calc.totalPay < best.totalPay) best = calc;
        }
        return best;
    }

    /* ------------------------
       핵심 계산: 주문 2건 최적
       (라인 단위 분할: 2^N)
       쿠폰은 주문당 1장, 쿠폰 1회성
       ------------------------ */
    private SplitCalc bestForTwoOrders(List<ProductItem> products, List<Coupon> coupons,
                                       int thAmount, int thOff, boolean promoBeforeCoupon,
                                       int shippingFee, int freeShip) {

        int n = products.size();
        SplitCalc best = null;

        // 쿠폰 배치 경우: (A에 null 포함) x (B에 null 포함, 단 A에 쓴 쿠폰 제외)
        List<Coupon> couponOptions = new ArrayList<>();
        couponOptions.add(null);
        couponOptions.addAll(coupons);

        for (int mask = 0; mask < (1 << n); mask++) {
            List<ProductItem> a = new ArrayList<>();
            List<ProductItem> b = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) a.add(products.get(i));
                else b.add(products.get(i));
            }

            // 둘 중 하나가 비면 '나눠 사기' 의미가 약하니 제외
            if (a.isEmpty() || b.isEmpty()) continue;

            for (Coupon ca : couponOptions) {
                for (Coupon cb : couponOptions) {
                    if (ca != null && cb != null && sameCoupon(ca, cb)) continue;

                    OrderCalc orderA = calcOrder(a, ca, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip);
                    OrderCalc orderB = calcOrder(b, cb, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip);

                    long total = orderA.totalPay + orderB.totalPay;
                    String plan = "주문 A:\n" + orderA.detail + "\n\n주문 B:\n" + orderB.detail;

                    SplitCalc candidate = new SplitCalc(total, plan);
                    if (best == null || candidate.totalPay < best.totalPay) best = candidate;
                }
            }
        }

        // 분할이 불가능하거나(상품 1종 등) 전부 제외된 경우 대비
        if (best == null) {
            // "나눠 사기 불가"로 표시
            OrderCalc single = bestForOneOrder(products, coupons, thAmount, thOff, promoBeforeCoupon, shippingFee, freeShip);
            best = new SplitCalc(Long.MAX_VALUE / 4, "나눠 구매 후보 없음(상품이 1종이거나 분할 조건 제외).");
        }
        return best;
    }

    private boolean sameCoupon(Coupon a, Coupon b) {
        // name이 없으면 객체 동일성으로
        if (a.getName() != null && b.getName() != null) return a.getName().equals(b.getName());
        return a == b;
    }

    /* ------------------------
       주문 1건 계산 파이프라인
       - 상품 자체 할인(라인별)
       - 프로모션(금액대)
       - 쿠폰(1장)
       - 배송(무료배송 조건)
       ------------------------ */
    private OrderCalc calcOrder(List<ProductItem> products, Coupon coupon,
                                int thAmount, int thOff, boolean promoBeforeCoupon,
                                int shippingFee, int freeShip) {

        long base = productsBase(products);

        // 1) 상품 자체 할인 적용 후 금액
        long afterProductDiscount = applyProductDiscounts(products);

        long amount = afterProductDiscount;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("상품합계(할인전): %,d원\n", base));
        sb.append(String.format("상품할인 적용 후: %,d원\n", afterProductDiscount));

        // 2) 프로모션/쿠폰 순서
        if (promoBeforeCoupon) {
            long beforePromo = amount;
            amount = applyThresholdPromo(amount, thAmount, thOff);
            sb.append(String.format("금액대할인: %,d원 → %,d원\n", beforePromo, amount));

            long beforeCoupon = amount;
            amount = applyCoupon(amount, coupon);
            sb.append(String.format("쿠폰(%s): %,d원 → %,d원\n", couponLabel(coupon), beforeCoupon, amount));
        } else {
            long beforeCoupon = amount;
            amount = applyCoupon(amount, coupon);
            sb.append(String.format("쿠폰(%s): %,d원 → %,d원\n", couponLabel(coupon), beforeCoupon, amount));

            long beforePromo = amount;
            amount = applyThresholdPromo(amount, thAmount, thOff);
            sb.append(String.format("금액대할인: %,d원 → %,d원\n", beforePromo, amount));
        }

        // 3) 배송비 (여기서는 “할인 적용 후 금액” 기준으로 무료배송 판단)
        long merchFinal = amount;
        long ship = (freeShip > 0 && merchFinal >= freeShip) ? 0 : shippingFee;

        long totalPay = merchFinal + ship;

        sb.append(String.format("무료배송 조건: %,d원 이상 / 배송비: %,d원\n", (long) freeShip, (long) shippingFee));
        sb.append(String.format("배송비 적용: %,d원\n", ship));
        sb.append(String.format("주문 최종 결제: %,d원\n", totalPay));

        return new OrderCalc(totalPay, sb.toString().trim());
    }

    private String couponLabel(Coupon c) {
        if (c == null) return "미사용";
        if (c.getName() != null && !c.getName().isBlank()) return c.getName();
        return c.getType() + ":" + c.getValue();
    }

    /* ------------------------
       계산 유틸
       ------------------------ */
    private long productsBase(List<ProductItem> products) {
        long sum = 0;
        for (ProductItem p : products) {
            sum += (long) nz(p.getPrice()) * (long) nz(p.getQuantity());
        }
        return sum;
    }

    private long applyProductDiscounts(List<ProductItem> products) {
        long sum = 0;
        for (ProductItem p : products) {
            long line = (long) nz(p.getPrice()) * (long) nz(p.getQuantity());
            String type = p.getDiscountType() == null ? "none" : p.getDiscountType();
            double v = nzD(p.getDiscountValue());

            if ("fixed".equals(type)) {
                // “라인 기준” 정액 할인(원)
                line = Math.max(0, line - Math.round(v));
            } else if ("percent".equals(type)) {
                double pct = Math.max(0, Math.min(100, v));
                line = Math.max(0, Math.round(line * (1 - pct / 100.0)));
            }
            sum += line;
        }
        return sum;
    }

    private long applyThresholdPromo(long amount, int threshold, int off) {
        if (threshold <= 0 || off <= 0) return amount;
        return (amount >= threshold) ? Math.max(0, amount - off) : amount;
    }

    private long applyCoupon(long amount, Coupon coupon) {
        if (coupon == null) return amount;

        int minSpend = nz(coupon.getMinSpend());
        if (amount < minSpend) return amount; // 조건 미달이면 미적용

        String type = coupon.getType();
        double value = nzD(coupon.getValue());
        int cap = nz(coupon.getMaxDiscount());

        long discounted = amount;

        if ("fixed".equals(type)) {
            long off = Math.max(0, Math.round(value));
            discounted = Math.max(0, amount - off);
        } else if ("percent".equals(type)) {
            double pct = Math.max(0, Math.min(100, value));
            long off = Math.round(amount * (pct / 100.0));
            if (cap > 0) off = Math.min(off, cap);
            discounted = Math.max(0, amount - off);
        }

        return discounted;
    }

    private List<ProductItem> safeProducts(List<ProductItem> in) {
        if (in == null) return List.of();
        return in.stream()
                .filter(Objects::nonNull)
                .filter(ProductItem::isActive) 
                .filter(p -> nz(p.getPrice()) >= 0)
                .filter(p -> nz(p.getQuantity()) > 0)
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<Coupon> safeCoupons(List<Coupon> in) {
        if (in == null) return List.of();
        return in.stream()
                .filter(Objects::nonNull)
                .filter(Coupon::isActive)    
                .filter(c -> c.getType() != null)
                .filter(c -> nzD(c.getValue()) > 0)
                .limit(3)
                .collect(Collectors.toList());
    }

    private int nz(Integer v) { return v == null ? 0 : v; }
    private double nzD(Double v) { return v == null ? 0.0 : v; }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private String buildDetail(List<ProductItem> products, List<Coupon> coupons,
                               int thAmount, int thOff, boolean promoBeforeCoupon,
                               int shippingFee, int freeShip,
                               long baselinePay,
                               OrderCalc bestSingle,
                               SplitCalc bestSplit,
                               boolean splitIsBetter) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== 입력 요약 ===\n");
        sb.append("상품(최대5):\n");
        for (ProductItem p : products) {
            sb.append(String.format("- %s / %,d원 x %d개 / 상품할인:%s(%.2f)\n",
                    (p.getName() == null ? "상품" : p.getName()),
                    (long) nz(p.getPrice()), nz(p.getQuantity()),
                    (p.getDiscountType() == null ? "none" : p.getDiscountType()),
                    nzD(p.getDiscountValue())));
        }
        sb.append("\n쿠폰(보유 최대3):\n");
        for (Coupon c : coupons) {
            sb.append(String.format("- %s / %s %.2f / 최소 %,d원 / 캡 %,d원\n",
                    (c.getName() == null ? "쿠폰" : c.getName()),
                    c.getType(), nzD(c.getValue()),
                    (long) nz(c.getMinSpend()), (long) nz(c.getMaxDiscount())));
        }

        sb.append("\n프로모션(금액대): ");
        sb.append(String.format("%,d원 이상 %,d원 할인 / 순서:%s\n",
                (long) thAmount, (long) thOff, promoBeforeCoupon ? "프로모션→쿠폰" : "쿠폰→프로모션"));

        sb.append(String.format("배송: 배송비 %,d원 / 무료배송 %,d원 이상\n",
                (long) shippingFee, (long) freeShip));

        sb.append("\n=== 기준(혜택없음) ===\n");
        sb.append(String.format("기준 결제(상품합+배송비): %,d원\n", baselinePay));

        sb.append("\n=== 1번에 구매 최적 ===\n");
        sb.append(bestSingle.detail);

        sb.append("\n\n=== 2번으로 나눠 구매 최적 ===\n");
        sb.append(bestSplit.plan);

        sb.append("\n\n=== 최종 선택 ===\n");
        sb.append(splitIsBetter ? "2번으로 나눠 구매가 더 저렴\n" : "1번에 구매가 더 저렴\n");

        return sb.toString().trim();
    }

    private static class OrderCalc {
        final long totalPay;
        final String detail;
        OrderCalc(long totalPay, String detail) {
            this.totalPay = totalPay;
            this.detail = detail;
        }
    }

    private static class SplitCalc {
        final long totalPay;
        final String plan;
        SplitCalc(long totalPay, String plan) {
            this.totalPay = totalPay;
            this.plan = plan;
        }
    }
}
