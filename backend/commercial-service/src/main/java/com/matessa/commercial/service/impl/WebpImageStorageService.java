package com.matessa.commercial.service.impl;

import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.service.ImageStorageService;
import com.matessa.commercial.storage.ObjectStore;
import com.matessa.commercial.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;

/**
 * Converts any uploaded raster image to WebP and stores it in the shared {@link ObjectStore}
 * (S3/MinIO in production, a directory in development). WebP is produced via the
 * {@code webp-imageio} ImageIO plugin (bundled native libs).
 *
 * <p>The object key is {@code <store>/<32-hex>.webp}, so each store's images live under its
 * own prefix; the returned URL ({@code /media/<store>/<32-hex>.webp}, served by
 * {@code MediaController}) goes into {@code ProductImage.url}.
 *
 * <p>Image dimensions are read from the header <em>before</em> decoding, and anything over
 * {@value #MAX_PIXELS_PER_SIDE}px on a side is refused: a tiny compressed file can declare an
 * enormous canvas (a decompression bomb) and exhaust the heap during decode.
 */
@Service
public class WebpImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(WebpImageStorageService.class);
    private static final long MAX_BYTES = 8L * 1024 * 1024;   // 8 MB upload cap
    static final int MAX_PIXELS_PER_SIDE = 4096;

    private final ObjectStore objectStore;
    private final float quality;

    public WebpImageStorageService(ObjectStore objectStore,
                                   @Value("${app.media.webp-quality:0.82}") float quality) {
        this.objectStore = objectStore;
        this.quality = quality;
    }

    @Override
    public String storeAsWebp(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No image file was provided.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Image exceeds the 8 MB upload limit.");
        }

        String store = TenantContext.getTenantId();
        if (store == null) {
            throw new IllegalStateException("Image upload outside a store context");
        }
        checkDimensions(file);
        BufferedImage image = decode(file);
        byte[] webp = encodeWebp(image);

        String key = store + "/" + UUID.randomUUID().toString().replace("-", "") + ".webp";
        try {
            objectStore.put(key, webp, "image/webp");
        } catch (RuntimeException e) {
            log.error("Failed to store WebP image {}", key, e);
            throw new BadRequestException("Could not store the image. Please try again.");
        }
        return "/media/" + key;
    }

    /** Reads width/height from the image header without decoding the pixels. */
    private void checkDimensions(MultipartFile file) {
        try (InputStream in = file.getInputStream(); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new BadRequestException("Unsupported or corrupt image file. Upload a PNG, JPEG, or WebP.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BadRequestException("Unsupported or corrupt image file. Upload a PNG, JPEG, or WebP.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_PIXELS_PER_SIDE || h > MAX_PIXELS_PER_SIDE) {
                    throw new BadRequestException("Image is " + w + "×" + h + " px; the limit is "
                            + MAX_PIXELS_PER_SIDE + " px on each side.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded image.");
        }
    }

    private BufferedImage decode(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new BadRequestException("Unsupported or corrupt image file. Upload a PNG, JPEG, or WebP.");
            }
            return image;
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded image.");
        }
    }

    private byte[] encodeWebp(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            // Fail loudly: silently storing a non-WebP would break the "always WebP" guarantee.
            throw new IllegalStateException("No WebP ImageIO writer on the classpath.");
        }
        ImageWriter writer = writers.next();
        var out = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if (params.getCompressionType() == null && params.getCompressionTypes() != null
                        && params.getCompressionTypes().length > 0) {
                    params.setCompressionType(params.getCompressionTypes()[0]);
                }
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new BadRequestException("Could not encode the image as WebP.");
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
