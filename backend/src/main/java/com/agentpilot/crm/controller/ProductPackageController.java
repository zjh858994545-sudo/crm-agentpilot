package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.ProductPackage;
import com.agentpilot.crm.service.ProductPackageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAuthority('product:read')")
public class ProductPackageController {
    private final ProductPackageService productPackageService;

    public ProductPackageController(ProductPackageService productPackageService) {
        this.productPackageService = productPackageService;
    }

    @GetMapping("/packages")
    public ApiResponse<List<ProductPackage>> packages(@RequestParam(required = false) String industry) {
        LambdaQueryWrapper<ProductPackage> wrapper = new LambdaQueryWrapper<ProductPackage>()
                .eq(ProductPackage::getStatus, "ACTIVE")
                .orderByAsc(ProductPackage::getId);
        if (industry != null && !industry.isBlank()) {
            wrapper.eq(ProductPackage::getIndustry, industry);
        }
        return ApiResponse.ok(productPackageService.list(wrapper));
    }
}
