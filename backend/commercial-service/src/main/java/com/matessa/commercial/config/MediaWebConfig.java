package com.matessa.commercial.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Serves stored product images statically at {@code /media/**} from the media directory
 * (a mounted volume in Docker). Uploaded images are WebP; this is how they are delivered
 * to the storefront/admin.
 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final String mediaRoot;

    public MediaWebConfig(@Value("${app.media.dir:/app/media/products}") String mediaDir) {
        // Serve the parent of the products dir so /media/products/<file>.webp resolves.
        Path products = Path.of(mediaDir).toAbsolutePath().normalize();
        this.mediaRoot = products.getParent().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Filenames are content-addressed (a changed image = a new hash = a new URL), so images
        // are immutable: browsers/CDN edges may cache them for a year and never revalidate.
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaRoot)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
