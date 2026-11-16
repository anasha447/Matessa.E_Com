package com.matessa.commercial.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.file.Path;

/**
 * Picks the image store from {@code app.media.store}:
 * {@code s3} (production; env {@code APP_MEDIA_STORE=s3} plus the {@code APP_MEDIA_S3_*}
 * settings) or {@code local} (default; a directory, single instance only).
 */
@Configuration(proxyBeanMethods = false)
public class ObjectStoreConfig {

    @Bean
    ObjectStore objectStore(@Value("${app.media.store:local}") String kind,
                            @Value("${app.media.dir:./media}") String dir,
                            @Value("${app.media.s3.endpoint:}") String endpoint,
                            @Value("${app.media.s3.region:us-east-1}") String region,
                            @Value("${app.media.s3.bucket:matessa-media}") String bucket,
                            @Value("${app.media.s3.access-key:}") String accessKey,
                            @Value("${app.media.s3.secret-key:}") String secretKey) {
        if (!"s3".equalsIgnoreCase(kind)) {
            return new LocalObjectStore(Path.of(dir));
        }
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("app.media.store=s3 needs APP_MEDIA_S3_ACCESS_KEY and APP_MEDIA_S3_SECRET_KEY");
        }
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // MinIO and most S3-compatible stores want path-style URLs (http://host/bucket/key).
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        S3ObjectStore store = new S3ObjectStore(builder.build(), bucket);
        store.ensureBucket();
        return store;
    }
}
