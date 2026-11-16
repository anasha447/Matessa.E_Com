package com.matessa.commercial.storage;

import java.util.Optional;

/**
 * Where uploaded product images live. One interface, two homes:
 *
 * <ul>
 *   <li>{@link S3ObjectStore} — any S3-compatible bucket (MinIO on the VPS, or a cloud bucket).
 *       Shared by every commercial-service instance, so scaling the service out does not
 *       scatter images across containers. The production setting.</li>
 *   <li>{@link LocalObjectStore} — a directory. Fine for a single instance in development and
 *       for tests; with more than one instance each would only see its own uploads.</li>
 * </ul>
 *
 * Keys look like {@code <store>/<32-hex>.webp}; images uploaded before stores were separated
 * keep their original {@code products/<32-hex>.webp} key so existing URLs stay valid.
 */
public interface ObjectStore {

    /** Writes (or overwrites) an object. */
    void put(String key, byte[] bytes, String contentType);

    /** Reads an object, or empty when there is none under that key. */
    Optional<StoredObject> get(String key);

    record StoredObject(byte[] bytes, String contentType) {}
}
