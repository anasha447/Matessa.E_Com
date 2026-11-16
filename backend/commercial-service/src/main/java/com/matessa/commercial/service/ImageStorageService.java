package com.matessa.commercial.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Persists an uploaded product image, converting it to WebP first. This is the
 * authoritative conversion point: whatever a client uploads (PNG/JPEG/…), what gets
 * stored and served is always {@code .webp}. The returned value is the public URL path
 * to reference from {@code ProductImage.url}.
 */
public interface ImageStorageService {

    /**
     * Decode the uploaded image, re-encode it as WebP, and store it.
     *
     * @param file the multipart upload (raster image; PNG/JPEG/WebP/…)
     * @return the public URL path of the stored WebP, e.g. {@code /media/matessa/ab12…cd.webp}
     */
    String storeAsWebp(MultipartFile file);
}
