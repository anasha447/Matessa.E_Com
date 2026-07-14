package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.ProductImageRequest;
import com.matessa.commercial.dto.response.ProductImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageResponse> listForProduct(Long productId);

    ProductImageResponse add(Long productId, ProductImageRequest request);

    /**
     * Upload a binary image: it is converted to WebP, stored, and registered as a product
     * image. Metadata (variant scope, alt text, order, primary flag) comes from the parts.
     */
    ProductImageResponse addUploaded(Long productId, MultipartFile file, Long variantId,
                                     String altText, int displayOrder, boolean primary);

    ProductImageResponse update(Long imageId, ProductImageRequest request);

    void delete(Long imageId);
}
