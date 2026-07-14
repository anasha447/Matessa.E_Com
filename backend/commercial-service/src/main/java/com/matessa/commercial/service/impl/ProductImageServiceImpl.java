package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.ProductImageRequest;
import com.matessa.commercial.dto.response.ProductImageResponse;
import com.matessa.commercial.entity.ProductImage;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.mapper.ProductImageMapper;
import com.matessa.commercial.repository.ProductImageRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.service.ImageStorageService;
import com.matessa.commercial.service.ProductImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageMapper imageMapper;
    private final ImageStorageService imageStorage;

    public ProductImageServiceImpl(ProductImageRepository imageRepository,
                                   ProductRepository productRepository,
                                   ProductVariantRepository variantRepository,
                                   ProductImageMapper imageMapper,
                                   ImageStorageService imageStorage) {
        this.imageRepository = imageRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageMapper = imageMapper;
        this.imageStorage = imageStorage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> listForProduct(Long productId) {
        ensureProductExists(productId);
        return imageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId)
                .stream().map(imageMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ProductImageResponse add(Long productId, ProductImageRequest request) {
        ensureProductExists(productId);
        validateVariant(productId, request.variantId());
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        apply(image, request);
        if (image.isPrimary()) {
            clearOtherPrimaries(productId, null);   // new image becomes the sole primary
        }
        return imageMapper.toResponse(imageRepository.save(image));
    }

    @Override
    @Transactional
    public ProductImageResponse addUploaded(Long productId, MultipartFile file, Long variantId,
                                            String altText, int displayOrder, boolean primary) {
        ensureProductExists(productId);
        validateVariant(productId, variantId);
        // Convert to WebP + store first; only then persist the row (so a failed conversion
        // never leaves a dangling image record).
        String url = imageStorage.storeAsWebp(file);
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setVariantId(variantId);
        image.setUrl(url);
        image.setAltText(altText);
        image.setDisplayOrder(displayOrder);
        image.setPrimary(primary);
        if (primary) {
            clearOtherPrimaries(productId, null);
        }
        return imageMapper.toResponse(imageRepository.save(image));
    }

    @Override
    @Transactional
    public ProductImageResponse update(Long imageId, ProductImageRequest request) {
        ProductImage image = findImage(imageId);
        validateVariant(image.getProductId(), request.variantId());
        apply(image, request);
        if (image.isPrimary()) {
            clearOtherPrimaries(image.getProductId(), image.getId());
        }
        return imageMapper.toResponse(imageRepository.save(image));
    }

    @Override
    @Transactional
    public void delete(Long imageId) {
        imageRepository.delete(findImage(imageId));   // hard delete — images are not referenced cross-service
    }

    private ProductImage findImage(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    /** A variant-scoped image must reference a variant that belongs to this product. */
    private void validateVariant(Long productId, Long variantId) {
        if (variantId == null) {
            return;
        }
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BadRequestException("Variant not found: " + variantId));
        if (!variant.getProductId().equals(productId)) {
            throw new BadRequestException(
                    "Variant " + variantId + " does not belong to product " + productId);
        }
    }

    /** Keep at most one primary image per product. */
    private void clearOtherPrimaries(Long productId, Long keepImageId) {
        for (ProductImage other : imageRepository.findByProductIdAndPrimaryTrue(productId)) {
            if (keepImageId == null || !other.getId().equals(keepImageId)) {
                other.setPrimary(false);
                imageRepository.save(other);
            }
        }
    }

    private void apply(ProductImage image, ProductImageRequest request) {
        image.setVariantId(request.variantId());
        image.setUrl(request.url());
        image.setAltText(request.altText());
        image.setDisplayOrder(request.displayOrder());
        image.setPrimary(request.primary());
    }
}
