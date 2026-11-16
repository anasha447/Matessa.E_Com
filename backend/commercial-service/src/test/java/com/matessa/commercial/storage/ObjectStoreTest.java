package com.matessa.commercial.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MinIOContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Both image stores honour the same contract; S3 is exercised against a real MinIO. */
class ObjectStoreTest {

    /** Started on demand; the S3 test is skipped (not failed) when the image cannot be pulled. */
    static MinIOContainer MINIO;

    @AfterAll
    static void stop() {
        if (MINIO != null) {
            MINIO.stop();
        }
    }

    @TempDir
    Path dir;

    @Test
    void local_putGet_andRefusesKeysOutsideItsRoot() {
        LocalObjectStore store = new LocalObjectStore(dir);
        contract(store);
        assertThatThrownBy(() -> store.put("../escape.webp", new byte[]{1}, "image/webp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void s3_putGet_sharedBucket() {
        if (MINIO == null) {
            MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");
            try {
                minio.start();
            } catch (RuntimeException e) {
                Assumptions.abort("MinIO image unavailable, S3 store not exercised: " + e.getMessage());
            }
            MINIO = minio;
        }
        String endpoint = MINIO.getS3URL().replace("localhost", System.getProperty("test.minio.host", "localhost"));
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        S3ObjectStore store = new S3ObjectStore(client, "test-media");
        store.ensureBucket();
        store.ensureBucket(); // idempotent
        contract(store);

        // A second instance (another replica) sees what the first one wrote.
        S3ObjectStore replica = new S3ObjectStore(client, "test-media");
        assertThat(replica.get("matessa/abc.webp")).isPresent();
    }

    private static void contract(ObjectStore store) {
        byte[] bytes = "webp-bytes".getBytes(StandardCharsets.UTF_8);
        store.put("matessa/abc.webp", bytes, "image/webp");
        ObjectStore.StoredObject got = store.get("matessa/abc.webp").orElseThrow();
        assertThat(got.bytes()).isEqualTo(bytes);
        assertThat(got.contentType()).isEqualTo("image/webp");
        assertThat(store.get("adventure/abc.webp")).isEmpty();
        assertThat(store.get("matessa/missing.webp")).isEmpty();
    }
}
