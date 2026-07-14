package com.matessa.commercial.service.impl;

import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

/**
 * Converts any uploaded raster image to WebP and writes it to the configured media
 * directory. WebP is produced via the {@code webp-imageio} ImageIO plugin (bundled
 * native libs). The stored file is served statically at {@code /media/**} (see
 * {@code WebConfig}); the returned path goes into {@code ProductImage.url}.
 */
@Service
public class WebpImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(WebpImageStorageService.class);
    private static final long MAX_BYTES = 8L * 1024 * 1024;   // 8 MB upload cap
    private static final String URL_PREFIX = "/media/products/";

    private final Path storageDir;
    private final float quality;

    public WebpImageStorageService(
            @Value("${app.media.dir:/app/media/products}") String storageDir,
            @Value("${app.media.webp-quality:0.82}") float quality) {
        this.storageDir = Path.of(storageDir);
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

        BufferedImage image = decode(file);
        byte[] webp = encodeWebp(image);

        String filename = UUID.randomUUID().toString().replace("-", "") + ".webp";
        try {
            Files.createDirectories(storageDir);
            Files.write(storageDir.resolve(filename), webp);
        } catch (IOException e) {
            log.error("Failed to write WebP image to {}", storageDir, e);
            throw new BadRequestException("Could not store the image. Please try again.");
        }
        return URL_PREFIX + filename;
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
