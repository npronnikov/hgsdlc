package ru.hgd.sdlc.registry.domain.model.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sha256Hash")
class Sha256HashTest {

    private static final String VALID_HEX_64 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Nested
    @DisplayName("of(String)")
    class OfString {

        @Test
        @DisplayName("should create from valid 64-character hex string")
        void shouldCreateFromValidHex() {
            Sha256Hash hash = Sha256Hash.of(VALID_HEX_64);

            assertEquals(VALID_HEX_64, hash.hex());
        }

        @Test
        @DisplayName("should normalize to lowercase")
        void shouldNormalizeToLowercase() {
            Sha256Hash hash = Sha256Hash.of("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");

            assertEquals(VALID_HEX_64, hash.hex());
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            Sha256Hash hash = Sha256Hash.of("  " + VALID_HEX_64 + "  ");

            assertEquals(VALID_HEX_64, hash.hex());
        }

        @Test
        @DisplayName("should reject null")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.of(null));
        }

        @Test
        @DisplayName("should reject blank string")
        void shouldRejectBlank() {
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.of("   "));
        }

        @Test
        @DisplayName("should reject too short")
        void shouldRejectTooShort() {
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.of("abc123"));
        }

        @Test
        @DisplayName("should reject too long")
        void shouldRejectTooLong() {
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.of(VALID_HEX_64 + "00"));
        }

        @Test
        @DisplayName("should reject invalid hex characters")
        void shouldRejectInvalidHex() {
            assertThrows(IllegalArgumentException.class,
                () -> Sha256Hash.of("g3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        }
    }

    @Nested
    @DisplayName("ofBytes(byte[])")
    class OfBytes {

        @Test
        @DisplayName("should create from 32 bytes")
        void shouldCreateFrom32Bytes() {
            byte[] bytes = new byte[32];
            for (int i = 0; i < 32; i++) {
                bytes[i] = (byte) i;
            }

            Sha256Hash hash = Sha256Hash.ofBytes(bytes);

            assertNotNull(hash.hex());
            assertEquals(64, hash.hex().length());
        }

        @Test
        @DisplayName("should reject null")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.ofBytes(null));
        }

        @Test
        @DisplayName("should reject wrong length")
        void shouldRejectWrongLength() {
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.ofBytes(new byte[31]));
            assertThrows(IllegalArgumentException.class, () -> Sha256Hash.ofBytes(new byte[33]));
        }
    }

    @Nested
    @DisplayName("toBytes()")
    class ToBytes {

        @Test
        @DisplayName("should convert back to bytes")
        void shouldConvertBackToBytes() {
            byte[] original = new byte[32];
            for (int i = 0; i < 32; i++) {
                original[i] = (byte) i;
            }

            Sha256Hash hash = Sha256Hash.ofBytes(original);
            byte[] converted = hash.toBytes();

            assertArrayEquals(original, converted);
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same hex")
        void shouldBeEqualForSameHex() {
            Sha256Hash hash1 = Sha256Hash.of(VALID_HEX_64);
            Sha256Hash hash2 = Sha256Hash.of(VALID_HEX_64);

            assertEquals(hash1, hash2);
            assertEquals(hash1.hashCode(), hash2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different hex")
        void shouldNotBeEqualForDifferentHex() {
            Sha256Hash hash1 = Sha256Hash.of(VALID_HEX_64);
            Sha256Hash hash2 = Sha256Hash.of("a3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

            assertNotEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("should include hex value")
        void shouldIncludeHexValue() {
            Sha256Hash hash = Sha256Hash.of(VALID_HEX_64);

            String str = hash.toString();

            assertTrue(str.contains(VALID_HEX_64));
        }
    }
}
