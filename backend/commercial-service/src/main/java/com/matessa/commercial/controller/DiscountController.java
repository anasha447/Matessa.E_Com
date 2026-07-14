package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.DiscountRequest;
import com.matessa.commercial.dto.response.DiscountResponse;
import com.matessa.commercial.service.DiscountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Automatic discounts. Reading what applies to a product is public (it affects the shown
 * price); creating/editing requires MANAGER/ADMIN. A discount targets a product or a
 * category (which cascades to sub-branches).
 */
@RestController
@RequestMapping("/api")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @GetMapping("/products/{productId}/discounts")
    public List<DiscountResponse> listForProduct(@PathVariable Long productId) {
        return discountService.listApplicableForProduct(productId);
    }

    @GetMapping("/discounts/{id}")
    public DiscountResponse get(@PathVariable Long id) {
        return discountService.get(id);
    }

    @PostMapping("/discounts")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountResponse create(@AuthenticationPrincipal Long userId,
                                   @Valid @RequestBody DiscountRequest request) {
        return discountService.create(request, userId);
    }

    @PutMapping("/discounts/{id}")
    public DiscountResponse update(@PathVariable Long id, @Valid @RequestBody DiscountRequest request) {
        return discountService.update(id, request);
    }

    @DeleteMapping("/discounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        discountService.delete(id);
    }
}
