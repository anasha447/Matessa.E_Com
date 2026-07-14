package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.ProductRequest;
import com.matessa.commercial.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    Page<ProductResponse> list(Long categoryId, Pageable pageable);

    ProductResponse get(Long id);

    ProductResponse getBySlug(String slug);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);
}
