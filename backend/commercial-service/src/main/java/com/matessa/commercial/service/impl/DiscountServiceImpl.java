package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.DiscountRequest;
import com.matessa.commercial.dto.response.DiscountResponse;
import com.matessa.commercial.entity.Discount;
import com.matessa.commercial.entity.DiscountType;
import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.DiscountMapper;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.DiscountRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.service.DiscountService;
import com.matessa.commercial.service.PricingService;
import com.matessa.commercial.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class DiscountServiceImpl implements DiscountService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DiscountRepository discountRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final DiscountMapper discountMapper;
    private final PricingService pricingService;

    public DiscountServiceImpl(DiscountRepository discountRepository,
                               ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               DiscountMapper discountMapper,
                               PricingService pricingService) {
        this.discountRepository = discountRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.discountMapper = discountMapper;
        this.pricingService = pricingService;
    }

    // Discounts change variant effective prices → evict the (price-carrying) variant list cache.
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public DiscountResponse create(DiscountRequest request, Long createdBy) {
        validate(request);
        Discount discount = new Discount();
        discount.setCreatedBy(createdBy);
        apply(discount, request);
        return discountMapper.toResponse(discountRepository.save(discount));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public DiscountResponse update(Long id, DiscountRequest request) {
        Discount discount = find(id);
        validate(request);
        apply(discount, request);
        return discountMapper.toResponse(discountRepository.save(discount));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public void delete(Long id) {
        discountRepository.delete(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponse get(Long id) {
        return discountMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscountResponse> listApplicableForProduct(Long productId) {
        return pricingService.activeDiscountsForProduct(productId).stream()
                .map(discountMapper::toResponse).toList();
    }

    private Discount find(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found: " + id));
    }

    private void validate(DiscountRequest request) {
        boolean hasProduct = request.productId() != null;
        boolean hasCategory = request.categoryId() != null;
        if (hasProduct == hasCategory) {
            throw new BadRequestException("Set exactly one of productId or categoryId");
        }
        if (hasProduct && !productRepository.existsById(request.productId())) {
            throw new BadRequestException("Product not found: " + request.productId());
        }
        if (hasCategory && !categoryRepository.existsById(request.categoryId())) {
            throw new BadRequestException("Category not found: " + request.categoryId());
        }
        if (!request.validTo().isAfter(request.validFrom())) {
            throw new BadRequestException("validTo must be after validFrom");
        }
        if (request.type() == DiscountType.PERCENTAGE && request.value().compareTo(HUNDRED) > 0) {
            throw new BadRequestException("A percentage discount cannot exceed 100");
        }
    }

    private void apply(Discount discount, DiscountRequest request) {
        discount.setProductId(request.productId());
        discount.setCategoryId(request.categoryId());
        discount.setType(request.type());
        discount.setValue(request.value());
        discount.setValidFrom(request.validFrom());
        discount.setValidTo(request.validTo());
        discount.setActive(request.active() == null || request.active());
    }
}
