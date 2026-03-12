package ru.hgd.sdlc.registry.application.signing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileKeyManager.
 */
class FileKeyManagerTest {

    @TempDir
    Path tempDir;

    private FileKeyManager keyManager;

    @BeforeEach
    void setUp() {
        keyManager = new FileKeyManager(tempDir.resolve("keys"));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up key files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // Delete in reverse order (files before dirs)
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }

    @Test
    void shouldCreateKeyDirectory() {
        assertTrue(Files.exists(keyManager.getKeyDirectory()));
    }

    @Test
    void shouldGenerateNewKey() {
        SigningKeyPair keyPair = keyManager.generateNewKey();

        assertNotNull(keyPair);
        assertEquals(32, keyPair.publicKeyBytes().length);
        assertEquals(32, keyPair.privateKeyBytes().length);
    }

    @Test
    void shouldReturnSameKeyOnSubsequentCalls() {
        SigningKeyPair key1 = keyManager.getSigningKey();
        SigningKeyPair key2 = keyManager.getSigningKey();

        assertArrayEquals(key1.publicKeyBytes(), key2.publicKeyBytes());
        assertArrayEquals(key1.privateKeyBytes(), key2.privateKeyBytes());
    }

    @Test
    void shouldGenerateNewKeyWhenRequested() {
        SigningKeyPair key1 = keyManager.getSigningKey();
        SigningKeyPair key2 = keyManager.generateNewKey();

        // New key should be different
        assertFalse(java.util.Arrays.equals(key1.publicKeyBytes(), key2.publicKeyBytes()));
    }

    @Test
    void shouldPersistKeyToFile() {
        keyManager.generateNewKey();

        // Check that a key file was created
        long keyFileCount;
        try (var stream = Files.list(keyManager.getKeyDirectory())) {
            keyFileCount = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".key"))
                .count();
        } catch (IOException e) {
            fail("Failed to list key files", e);
            return;
        }

        assertTrue(keyFileCount > 0, "At least one key file should exist");
    }

    @Test
    void shouldLoadExistingKey() {
        // Generate and get key ID
        SigningKeyPair originalKey = keyManager.generateNewKey();
        String keyId = keyManager.getCurrentKeyId();

        // Create a new manager to force reload from disk
        FileKeyManager newManager = new FileKeyManager(keyManager.getKeyDirectory());
        Optional<SigningKeyPair> loadedKey = newManager.loadKey(keyId);

        assertTrue(loadedKey.isPresent());
        assertArrayEquals(originalKey.publicKeyBytes(), loadedKey.get().publicKeyBytes());
        assertArrayEquals(originalKey.privateKeyBytes(), loadedKey.get().privateKeyBytes());
    }

    @Test
    void shouldReturnEmptyForNonExistentKey() {
        Optional<SigningKeyPair> key = keyManager.loadKey("non-existent-key");
        assertFalse(key.isPresent());
    }

    @Test
    void shouldReturnEmptyForNullKeyId() {
        Optional<SigningKeyPair> key = keyManager.loadKey(null);
        assertFalse(key.isPresent());
    }

    @Test
    void shouldReturnEmptyForBlankKeyId() {
        Optional<SigningKeyPair> key = keyManager.loadKey("   ");
        assertFalse(key.isPresent());
    }

    @Test
    void shouldGetCurrentKeyId() {
        keyManager.generateNewKey();

        String keyId = keyManager.getCurrentKeyId();

        assertNotNull(keyId);
        assertTrue(keyId.startsWith("key-"));
    }

    @Test
    void shouldGenerateKeyIdWithCurrentMonth() {
        keyManager.generateNewKey();

        String keyId = keyManager.getCurrentKeyId();

        // Key ID format: key-YYYY-MM-DD-HHmmss-nnnnnnnnn
        assertTrue(keyId.matches("key-\\d{4}-\\d{2}-\\d{2}-\\d{6}-\\d{9}"));
    }

    @Test
    void shouldLoadLatestKeyOnStartup() {
        // Generate a key
        SigningKeyPair originalKey = keyManager.generateNewKey();

        // Create a new manager (simulates restart)
        FileKeyManager newManager = new FileKeyManager(keyManager.getKeyDirectory());

        SigningKeyPair loadedKey = newManager.getSigningKey();

        assertArrayEquals(originalKey.publicKeyBytes(), loadedKey.publicKeyBytes());
    }

    @Test
    void shouldSupportKeyRotation() {
        // Generate first key
        SigningKeyPair key1 = keyManager.generateNewKey();
        String keyId1 = keyManager.getCurrentKeyId();

        // Generate second key
        SigningKeyPair key2 = keyManager.generateNewKey();
        String keyId2 = keyManager.getCurrentKeyId();

        // Keys should be different
        assertFalse(java.util.Arrays.equals(key1.publicKeyBytes(), key2.publicKeyBytes()));

        // Old key should still be loadable
        Optional<SigningKeyPair> loadedKey1 = keyManager.loadKey(keyId1);
        assertTrue(loadedKey1.isPresent());
        assertArrayEquals(key1.publicKeyBytes(), loadedKey1.get().publicKeyBytes());

        // Current key should be the new one
        Optional<SigningKeyPair> loadedKey2 = keyManager.loadKey(keyId2);
        assertTrue(loadedKey2.isPresent());
        assertArrayEquals(key2.publicKeyBytes(), loadedKey2.get().publicKeyBytes());
    }

    @Test
    void shouldSignWithLoadedKey() {
        SigningKeyPair keyPair = keyManager.getSigningKey();
        byte[] data = "Test data to sign".getBytes();

        byte[] signature = keyPair.sign(data);

        assertTrue(keyPair.verify(data, signature));
    }

    @Test
    void shouldHaveFluentAccessors() {
        keyManager.generateNewKey();

        assertNotNull(keyManager.getKeyDirectory());
        assertNotNull(keyManager.getCurrentKeyId());
    }

    @Test
    void shouldHandleMultipleKeyManagers() {
        // First manager generates a key
        SigningKeyPair key1 = keyManager.generateNewKey();

        // Second manager should load the same key
        FileKeyManager manager2 = new FileKeyManager(keyManager.getKeyDirectory());
        SigningKeyPair key2 = manager2.getSigningKey();

        assertArrayEquals(key1.publicKeyBytes(), key2.publicKeyBytes());
    }
}
