package com.jiwoo.noaats.controller;

import com.jiwoo.noaats.domain.DiscountResult;
import com.jiwoo.noaats.domain.ProductItem;
import com.jiwoo.noaats.form.DiscountForm;
import com.jiwoo.noaats.service.DiscountCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountCalculatorService calculatorService;

    @GetMapping("/")
    public String home(Model model) {
        DiscountForm form = new DiscountForm();

        // 기본 상품 1개는 넣어둠
        ProductItem p = new ProductItem();
        p.setName("상품1");
        p.setPrice(10000);
        p.setQuantity(1);
        p.setDiscountType("none");
        p.setDiscountValue(0.0);
        form.getProducts().add(p);

        form.setThresholdAmount(0);
        form.setThresholdOff(0);
        form.setPromoBeforeCoupon(true);

        form.setShippingFee(3000);
        form.setFreeShippingThreshold(30000);

        model.addAttribute("discountForm", form);
        return "index";
    }

    @PostMapping("/calculate")
    public String calculate(@ModelAttribute("discountForm") DiscountForm form, Model model) {
        if (form.getProducts() == null || form.getProducts().isEmpty()) {
            model.addAttribute("error", "상품을 최소 1개 이상 입력해줘.");
            return "index";
        }

        DiscountResult result = calculatorService.calculateBestStrategy(form);
        model.addAttribute("result", result);
        return "index";
    }
}
