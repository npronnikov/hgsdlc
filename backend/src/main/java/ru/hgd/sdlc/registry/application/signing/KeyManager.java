package ru.hgd.sdlc.registry.application.signing;

import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;

import java.util.Optional;

/**
 * Interface for managing signing keys.
 * Provides key storage, retrieval, and generation capabilities.
 */
public interface KeyManager {

    /**
     * Gets the current signing key.
     * If no key exists, one may be generated automatically depending on implementation.
     *
     * @return the current signing key pair
     * @throws SigningException if the key cannot be retrieved or generated
     */
    SigningKeyPair getSigningKey() throws SigningException;

    /**
     * Generates and stores a new key.
     * The new key becomes the current signing key.
     *
     * @return the newly generated key pair
     * @throws SigningException if key generation or storage fails
     */
    SigningKeyPair generateNewKey() throws SigningException;

    /**
     * Loads a key by its identifier.
     * Used for verification of historical signatures.
     *
     * @param keyId the key identifier
     * @return the key pair if found, empty otherwise
     * @throws SigningException if loading fails
     */
    Optional<SigningKeyPair> loadKey(String keyId) throws SigningException;

    /**
     * Returns the identifier of the current signing key.
     *
     * @return the current key ID
     * @throws SigningException if no current key is available
     */
    String getCurrentKeyId() throws SigningException;
}
