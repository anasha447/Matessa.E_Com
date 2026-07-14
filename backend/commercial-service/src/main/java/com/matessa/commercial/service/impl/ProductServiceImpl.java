package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.ProductRequest;
import com.matessa.commercial.dto.response.ProductResponse;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.ProductMapper;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.service.ProductService;
import com.matessa.commercial.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Long categoryId, Pageable pageable) {
        Page<Product> page = (categoryId == null)
                ? productRepository.findByActiveTrue(pageable)
                : productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
        return page.map(productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productMapper.toResponse(findProduct(id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PRODUCT, key = "#slug")
    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return productMapper.toResponse(product);
    }

    // A product write can change what any listing/PDP shows, so clear the product + variant
    // caches wholesale (allEntries) — a paginated/slug read can't be evicted by a single key.
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    })
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Product slug already exists: " + request.slug());
        }
        validateCategory(request.categoryId());
        Product product = new Product();
        apply(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    })
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        if (productRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new ConflictException("Product slug already exists: " + request.slug());
        }
        validateCategory(request.categoryId());
        apply(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    })
    public void delete(Long id) {
        Product product = findProduct(id);
        product.setActive(false); // soft delete
        productRepository.save(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private void validateCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BadRequestException("Category not found: " + categoryId);
        }
    }

    private void apply(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setSlug(request.slug());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        product.setBrand(request.brand());
        product.setActive(true);
    }
}
