package ru.hgd.sdlc.registry.application.signing;

import lombok.Getter;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * File-based key storage implementation.
 * Stores keys in ~/.sdlc/keys/ directory with secure file permissions.
 *
 * File format:
 * - Key files are named with the key ID (e.g., "key-2026-03.key")
 * - Each file contains Base64-encoded private key on first line,
 *   Base64-encoded public key on second line
 * - File permissions are set to 600 (owner read/write only)
 */
@Getter
public final class FileKeyManager implements KeyManager {

    private static final String KEY_DIRECTORY = ".sdlc/keys";
    private static final String KEY_FILE_EXTENSION = ".key";
    private static final String KEY_ID_PREFIX = "key-";
    private static final Set<PosixFilePermission> KEY_FILE_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );

    private final Path keyDirectory;
    private SigningKeyPair currentKey;
    private String currentKeyId;

    /**
     * Creates a FileKeyManager with the default key directory (~/.sdlc/keys).
     *
     * @throws SigningException if the key directory cannot be created
     */
    public FileKeyManager() {
        this.keyDirectory = getDefaultKeyDirectory();
        initializeKeyDirectory();
    }

    /**
     * Creates a FileKeyManager with a custom key directory.
     *
     * @param keyDirectory the directory to store keys
     * @throws SigningException if the key directory cannot be created
     */
    public FileKeyManager(Path keyDirectory) {
        this.keyDirectory = keyDirectory;
        initializeKeyDirectory();
    }

    @Override
    public synchronized SigningKeyPair getSigningKey() throws SigningException {
        if (currentKey != null) {
            return currentKey;
        }

        // Try to load the most recent key
        Optional<String> latestKeyId = findLatestKeyId();
        if (latestKeyId.isPresent()) {
            Optional<SigningKeyPair> loadedKey = loadKey(latestKeyId.get());
            if (loadedKey.isPresent()) {
                currentKey = loadedKey.get();
                currentKeyId = latestKeyId.get();
                return currentKey;
            }
        }

        // No key found, generate a new one
        return generateNewKey();
    }

    @Override
    public synchronized SigningKeyPair generateNewKey() throws SigningException {
        try {
            // Generate new key pair
            SigningKeyPair newKeyPair = SigningKeyPair.generate();

            // Generate key ID based on current date
            String keyId = generateKeyId();
            Path keyFile = keyDirectory.resolve(keyId + KEY_FILE_EXTENSION);

            // Write key file
            writeKeyFile(keyFile, newKeyPair);

            // Set current key
            currentKey = newKeyPair;
            currentKeyId = keyId;

            return newKeyPair;
        } catch (IllegalStateException e) {
            throw new SigningException("Failed to generate new key", e);
        }
    }

    @Override
    public synchronized Optional<SigningKeyPair> loadKey(String keyId) throws SigningException {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }

        Path keyFile = keyDirectory.resolve(keyId + KEY_FILE_EXTENSION);
        if (!Files.exists(keyFile)) {
            return Optional.empty();
        }

        try {
            SigningKeyPair keyPair = readKeyFile(keyFile);
            return Optional.of(keyPair);
        } catch (IOException e) {
            throw new SigningException("Failed to load key: " + keyId, e);
        }
    }

    @Override
    public synchronized String getCurrentKeyId() throws SigningException {
        if (currentKeyId != null) {
            return currentKeyId;
        }

        // Force load/generate key to get the ID
        getSigningKey();
        return currentKeyId;
    }

    /**
     * Initializes the key directory, creating it if necessary.
     */
    private void initializeKeyDirectory() throws SigningException {
        try {
            if (!Files.exists(keyDirectory)) {
                Files.createDirectories(keyDirectory);

                // Set directory permissions to 700 (owner only)
                try {
                    Files.setPosixFilePermissions(keyDirectory,
                        Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                        ));
                } catch (UnsupportedOperationException e) {
                    // Windows or non-POSIX filesystem - skip permission setting
                }
            }
        } catch (IOException e) {
            throw new SigningException("Failed to initialize key directory: " + keyDirectory, e);
        }
    }

    /**
     * Finds the most recent key ID by scanning the key directory.
     */
    private Optional<String> findLatestKeyId() throws SigningException {
        try (Stream<Path> files = Files.list(keyDirectory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(KEY_FILE_EXTENSION))
                .filter(p -> p.getFileName().toString().startsWith(KEY_ID_PREFIX))
                .map(p -> p.getFileName().toString().replace(KEY_FILE_EXTENSION, ""))
                .max(String::compareTo);
        } catch (IOException e) {
            throw new SigningException("Failed to scan key directory", e);
        }
    }

    /**
     * Generates a unique key ID based on the current date and time with nanoseconds.
     * Format: key-YYYY-MM-DD-HHmmss-nnnnnnnnn
     */
    private String generateKeyId() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        return KEY_ID_PREFIX + timestamp + "-" + String.format("%09d", now.getNano());
    }

    /**
     * Writes a key pair to a file with secure permissions using atomic write.
     * Writes to a temporary file first, then atomically renames to target.
     */
    private void writeKeyFile(Path file, SigningKeyPair keyPair) throws SigningException {
        Path tempFile = null;
        try {
            // Format: first line is private key (Base64), second line is public key (Base64)
            String content = keyPair.privateKeyBase64() + "\n" + keyPair.publicKeyBase64() + "\n";

            // Write to temp file first for atomic operation
            tempFile = Files.createTempFile(file.getParent(), "key-write-", ".tmp");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            // Set file permissions to 600 (owner read/write only) on temp file
            try {
                Files.setPosixFilePermissions(tempFile, KEY_FILE_PERMISSIONS);
            } catch (UnsupportedOperationException e) {
                // Windows or non-POSIX filesystem - skip permission setting
            }

            // Atomically move temp file to target location
            Files.move(tempFile, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                       java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempFile = null; // Mark as successfully moved

        } catch (IOException e) {
            throw new SigningException("Failed to write key file: " + file, e);
        } finally {
            // Clean up temp file if move failed
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
        }
    }

    /**
     * Reads a key pair from a file.
     */
    private SigningKeyPair readKeyFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String[] lines = content.trim().split("\n");

        if (lines.length < 2) {
            throw new IOException("Invalid key file format: expected 2 lines");
        }

        byte[] privateKeyBytes = Base64.getDecoder().decode(lines[0].trim());
        byte[] publicKeyBytes = Base64.getDecoder().decode(lines[1].trim());

        return SigningKeyPair.of(privateKeyBytes, publicKeyBytes);
    }

    /**
     * Gets the default key directory path (~/.sdlc/keys).
     */
    private static Path getDefaultKeyDirectory() {
        String homeDir = System.getProperty("user.home");
        if (homeDir == null) {
            throw new SigningException("Cannot determine user home directory");
        }
        return Path.of(homeDir, KEY_DIRECTORY);
    }
}
