package com.matessa.commercial.storage;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/**
 * Objects in an S3-compatible bucket (MinIO in docker-compose; any S3 in production).
 * Every commercial-service instance reads and writes the same bucket, which is what lets the
 * service run as more than one replica. The bucket is private: images are served through
 * commercial-service ({@code MediaController}), never straight from the bucket.
 */
public class S3ObjectStore implements ObjectStore {

    private final S3Client s3;
    private final String bucket;

    public S3ObjectStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Creates the bucket on first boot if it does not exist yet. */
    public void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object =
                    s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            String type = object.response().contentType();
            return Optional.of(new StoredObject(object.asByteArray(), type == null ? "image/webp" : type));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
