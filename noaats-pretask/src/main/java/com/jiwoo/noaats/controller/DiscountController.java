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

        form.getProducts().add(new ProductItem());

        form.setThresholdAmount(0);
        form.setThresholdOff(0);
        form.setPromoBeforeCoupon(true);

        model.addAttribute("discountForm", form);
        return "index";
    }

    @PostMapping("/calculate")
    public String calculate(@ModelAttribute("discountForm") DiscountForm form, Model model) {
    	boolean hasActiveProduct = form.getProducts() != null &&
    	            form.getProducts().stream().anyMatch(ProductItem::isActive);

    	if (!hasActiveProduct) {
    	        model.addAttribute("error", "상품을 최소 1개 이상 입력해주세요.");
    	        return "index";
    	    }

        DiscountResult result = calculatorService.calculateBestStrategy(form);
        model.addAttribute("result", result);
        return "index";
    }
}
