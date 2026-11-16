package com.matessa.commercial.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Objects as files under one root directory (development, tests, single instance).
 * Keys are resolved strictly inside the root: a key that would escape it is refused.
 */
public class LocalObjectStore implements ObjectStore {

    private final Path root;

    public LocalObjectStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + key, e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            return Optional.of(new StoredObject(Files.readAllBytes(resolve(key)), "image/webp"));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Key escapes the storage root: " + key);
        }
        return p;
    }
}
