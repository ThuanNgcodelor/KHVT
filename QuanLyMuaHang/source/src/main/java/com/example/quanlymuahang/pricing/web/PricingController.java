package com.example.quanlymuahang.pricing.web;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.pricing.application.PricingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prices")
@PreAuthorize("hasAuthority('*') or hasAuthority('PRICE_READ')")
public class PricingController {
    private final PricingService pricing;
    public PricingController(PricingService pricing) { this.pricing = pricing; }

    @GetMapping
    public Page<PricingService.PriceView> search(@RequestParam(defaultValue = "") String q,
                                                  @RequestParam(required = false) CurrencyCode currency,
                                                  @RequestParam(required = false) MaterialCategory category,
                                                  @PageableDefault(size = 25) Pageable pageable) {
        return pricing.search(q, currency, category, pageable);
    }

    @GetMapping("/latest")
    public PricingService.PriceView latest(@RequestParam(required = false) String materialCode,
                                           @RequestParam(required = false) String materialName,
                                           @RequestParam CurrencyCode currency) {
        return pricing.latest(materialCode, materialName, currency);
    }
}
