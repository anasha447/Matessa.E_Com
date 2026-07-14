package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.ProductImageRequest;
import com.matessa.commercial.dto.response.ProductImageResponse;
import com.matessa.commercial.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Product image gallery. Reads are public (part of the catalog); writes require MANAGER/ADMIN.
 * An image may be scoped to a variant (e.g. a colour) via {@code variantId}, or left general.
 */
@RestController
@RequestMapping("/api")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(ProductImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/products/{productId}/images")
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return imageService.listForProduct(productId);
    }

    @PostMapping("/products/{productId}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse add(@PathVariable Long productId,
                                    @Valid @RequestBody ProductImageRequest request) {
        return imageService.add(productId, request);
    }

    /**
     * Upload an image file (PNG/JPEG/WebP). It is converted to WebP server-side, stored,
     * and registered as a product image. Staff only. Multipart parts carry the metadata.
     */
    @PostMapping(value = "/products/{productId}/images/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse upload(@PathVariable Long productId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "variantId", required = false) Long variantId,
                                       @RequestParam(value = "altText", required = false) String altText,
                                       @RequestParam(value = "displayOrder", defaultValue = "0") int displayOrder,
                                       @RequestParam(value = "primary", defaultValue = "false") boolean primary) {
        return imageService.addUploaded(productId, file, variantId, altText, displayOrder, primary);
    }

    @PutMapping("/images/{imageId}")
    public ProductImageResponse update(@PathVariable Long imageId,
                                       @Valid @RequestBody ProductImageRequest request) {
        return imageService.update(imageId, request);
    }

    @DeleteMapping("/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long imageId) {
        imageService.delete(imageId);
    }
}
