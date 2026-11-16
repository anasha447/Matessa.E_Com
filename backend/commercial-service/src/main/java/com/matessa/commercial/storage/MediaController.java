package com.matessa.commercial.storage;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves product images from the {@link ObjectStore}, whichever backend it is.
 *
 * <p>URLs are {@code /media/<folder>/<32-hex>.webp}: {@code <folder>} is the store an image was
 * uploaded to, or {@code products} for images uploaded before stores were separated. Both
 * patterns are fixed-shape, so nothing but a stored image can be addressed here.
 *
 * <p>File names are random and never reused (a changed image is a new upload, a new URL), so
 * responses are immutable and cacheable for a year by browsers and any CDN in front.
 */
@RestController
public class MediaController {

    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final ObjectStore store;

    public MediaController(ObjectStore store) {
        this.store = store;
    }

    @GetMapping("/media/{folder:[a-z][a-z0-9_]{1,30}}/{file:[a-f0-9]{32}\\.webp}")
    public ResponseEntity<byte[]> image(@PathVariable String folder, @PathVariable String file) {
        return store.get(folder + "/" + file)
                .map(o -> ResponseEntity.ok()
                        .cacheControl(IMMUTABLE)
                        .contentType(MediaType.parseMediaType(o.contentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .body(o.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
