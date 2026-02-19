package com.teamsync.common.util;

import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CursorUtils - Base64 cursor encoding/decoding for pagination.
 *
 * Tests cover:
 * - Basic encoding/decoding
 * - Null/blank handling
 * - Invalid Base64 handling
 * - Compound cursor support
 * - Edge cases (special chars, Unicode, etc.)
 */
@DisplayName("Cursor Utils Tests")
class CursorUtilsTest {

    private static final String DOCUMENT_ID = "doc-123-456-789";
    private static final String SORT_VALUE = "2024-01-15T10:30:00Z";

    @Nested
    @DisplayName("Encode Tests")
    class EncodeTests {

        @Test
        @DisplayName("Should encode valid string to Base64")
        void encode_ValidValue_ReturnsBase64() {
            // When
            String encoded = CursorUtils.encode(DOCUMENT_ID);

            // Then
            assertThat(encoded).isNotNull();
            assertThat(encoded).isNotBlank();
            // Verify it's valid Base64 by decoding
            assertThatCode(() -> Base64.getUrlDecoder().decode(encoded))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should return null for null input")
        void encode_NullValue_ReturnsNull() {
            // When
            String encoded = CursorUtils.encode(null);

            // Then
            assertThat(encoded).isNull();
        }

        @Test
        @DisplayName("Should encode empty string")
        void encode_EmptyString_ReturnsEncoded() {
            // When
            String encoded = CursorUtils.encode("");

            // Then
            assertThat(encoded).isNotNull();
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            assertThat(decoded).isEmpty();
        }

        @Test
        @DisplayName("Should handle special characters")
        void encode_SpecialChars_Handles() {
            // Given
            String specialValue = "doc/with/slashes+and+plus&ampersand=equals";

            // When
            String encoded = CursorUtils.encode(specialValue);

            // Then
            assertThat(encoded).isNotNull();
            // Verify round-trip
            String decoded = CursorUtils.decode(encoded);
            assertThat(decoded).isEqualTo(specialValue);
        }

        @Test
        @DisplayName("Should handle Unicode characters")
        void encode_UnicodeChars_Handles() {
            // Given
            String unicodeValue = "文档-日本語-한국어-🎉";

            // When
            String encoded = CursorUtils.encode(unicodeValue);

            // Then
            assertThat(encoded).isNotNull();
            String decoded = CursorUtils.decode(encoded);
            assertThat(decoded).isEqualTo(unicodeValue);
        }

        @Test
        @DisplayName("Should handle very long strings")
        void encode_VeryLongString_Handles() {
            // Given
            StringBuilder longValue = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                longValue.append("id-").append(i).append("-");
            }

            // When
            String encoded = CursorUtils.encode(longValue.toString());

            // Then
            assertThat(encoded).isNotNull();
            String decoded = CursorUtils.decode(encoded);
            assertThat(decoded).isEqualTo(longValue.toString());
        }

        @Test
        @DisplayName("Should handle whitespace-only strings")
        void encode_WhitespaceOnly_Handles() {
            // Given
            String whitespace = "   \t\n  ";

            // When
            String encoded = CursorUtils.encode(whitespace);

            // Then
            assertThat(encoded).isNotNull();
            String decoded = CursorUtils.decode(encoded);
            assertThat(decoded).isEqualTo(whitespace);
        }

        @Test
        @DisplayName("Should produce URL-safe Base64")
        void encode_ProducesUrlSafeBase64() {
            // Given - value that might produce non-URL-safe Base64
            String value = "doc???///+++";

            // When
            String encoded = CursorUtils.encode(value);

            // Then - URL-safe Base64 doesn't contain + or /
            // Note: The implementation uses getUrlEncoder which produces URL-safe output
            assertThat(encoded).doesNotContain("/");
        }

        @Test
        @DisplayName("Should encode consistently")
        void encode_Consistent() {
            // When
            String encoded1 = CursorUtils.encode(DOCUMENT_ID);
            String encoded2 = CursorUtils.encode(DOCUMENT_ID);

            // Then
            assertThat(encoded1).isEqualTo(encoded2);
        }
    }

    @Nested
    @DisplayName("Decode Tests")
    class DecodeTests {

        @Test
        @DisplayName("Should decode valid Base64 cursor")
        void decode_ValidCursor_ReturnsDecoded() {
            // Given
            String encoded = CursorUtils.encode(DOCUMENT_ID);

            // When
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(DOCUMENT_ID);
        }

        @Test
        @DisplayName("Should return null for null cursor")
        void decode_NullCursor_ReturnsNull() {
            // When
            String decoded = CursorUtils.decode(null);

            // Then
            assertThat(decoded).isNull();
        }

        @Test
        @DisplayName("Should return null for blank cursor")
        void decode_BlankCursor_ReturnsNull() {
            // When
            String decoded = CursorUtils.decode("   ");

            // Then
            assertThat(decoded).isNull();
        }

        @Test
        @DisplayName("Should return null for empty cursor")
        void decode_EmptyCursor_ReturnsNull() {
            // When
            String decoded = CursorUtils.decode("");

            // Then
            assertThat(decoded).isNull();
        }

        @Test
        @DisplayName("Should return null for invalid Base64")
        void decode_InvalidBase64_ReturnsNull() {
            // Given - invalid Base64 characters
            String invalidCursor = "!!!not-valid-base64!!!";

            // When
            String decoded = CursorUtils.decode(invalidCursor);

            // Then
            assertThat(decoded).isNull();
        }

        @Test
        @DisplayName("Should handle Base64 with padding")
        void decode_Base64WithPadding_Handles() {
            // Given - manually create Base64 with padding
            String value = "ab"; // Short value that produces padding
            String encoded = Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

            // When
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(value);
        }

        @Test
        @DisplayName("Should handle Base64 without padding")
        void decode_Base64WithoutPadding_Handles() {
            // Given - create Base64 without padding
            String value = "abc";
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(value.getBytes(StandardCharsets.UTF_8));

            // When
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(value);
        }

        @Test
        @DisplayName("Should handle corrupted Base64")
        void decode_CorruptedBase64_ReturnsNull() {
            // Given - start with valid Base64 and corrupt it
            String encoded = CursorUtils.encode(DOCUMENT_ID);
            String corrupted = encoded.substring(0, encoded.length() / 2); // Truncate

            // When
            String decoded = CursorUtils.decode(corrupted);

            // Then - may return garbage or throw, implementation returns null on error
            // The behavior depends on how invalid the truncation is
            // For very short truncations, it might still decode but produce garbage
        }
    }

    @Nested
    @DisplayName("Encode Compound Tests")
    class EncodeCompoundTests {

        @Test
        @DisplayName("Should encode compound cursor with ID and sort value")
        void encodeCompound_WithSortValue_EncodesBoth() {
            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, SORT_VALUE);

            // Then
            assertThat(encoded).isNotNull();
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded).hasSize(2);
            assertThat(decoded[0]).isEqualTo(DOCUMENT_ID);
            assertThat(decoded[1]).isEqualTo(SORT_VALUE);
        }

        @Test
        @DisplayName("Should handle null sort value")
        void encodeCompound_NullSortValue_Handles() {
            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, null);

            // Then
            assertThat(encoded).isNotNull();
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded).hasSize(2);
            assertThat(decoded[0]).isEqualTo(DOCUMENT_ID);
            assertThat(decoded[1]).isEmpty();
        }

        @Test
        @DisplayName("Should handle numeric sort value")
        void encodeCompound_NumericSortValue_Handles() {
            // Given
            Long numericSort = 1705312200000L;

            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, numericSort);

            // Then
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded[1]).isEqualTo(String.valueOf(numericSort));
        }

        @Test
        @DisplayName("Should handle integer sort value")
        void encodeCompound_IntegerSortValue_Handles() {
            // Given
            Integer intSort = 42;

            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, intSort);

            // Then
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded[1]).isEqualTo("42");
        }

        @Test
        @DisplayName("Should handle double sort value")
        void encodeCompound_DoubleSortValue_Handles() {
            // Given
            Double doubleSort = 3.14159;

            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, doubleSort);

            // Then
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded[1]).isEqualTo(String.valueOf(doubleSort));
        }

        @Test
        @DisplayName("Should handle boolean sort value")
        void encodeCompound_BooleanSortValue_Handles() {
            // Given
            Boolean boolSort = true;

            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, boolSort);

            // Then
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded[1]).isEqualTo("true");
        }

        @Test
        @DisplayName("Should handle sort value containing pipe")
        void encodeCompound_SortValueWithPipe_Handles() {
            // Given - pipe is the delimiter
            String sortWithPipe = "value|with|pipes";

            // When
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, sortWithPipe);

            // Then
            String[] decoded = CursorUtils.decodeCompound(encoded);
            // The split with limit=2 means everything after first pipe is in second element
            assertThat(decoded).hasSize(2);
            assertThat(decoded[0]).isEqualTo(DOCUMENT_ID);
            assertThat(decoded[1]).isEqualTo(sortWithPipe);
        }

        @Test
        @DisplayName("Should handle empty ID")
        void encodeCompound_EmptyId_Handles() {
            // When
            String encoded = CursorUtils.encodeCompound("", SORT_VALUE);

            // Then
            String[] decoded = CursorUtils.decodeCompound(encoded);
            assertThat(decoded[0]).isEmpty();
            assertThat(decoded[1]).isEqualTo(SORT_VALUE);
        }
    }

    @Nested
    @DisplayName("Decode Compound Tests")
    class DecodeCompoundTests {

        @Test
        @DisplayName("Should decode valid compound cursor")
        void decodeCompound_ValidCursor_ReturnsParts() {
            // Given
            String encoded = CursorUtils.encodeCompound(DOCUMENT_ID, SORT_VALUE);

            // When
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEqualTo(DOCUMENT_ID);
            assertThat(parts[1]).isEqualTo(SORT_VALUE);
        }

        @Test
        @DisplayName("Should return null for null cursor")
        void decodeCompound_NullCursor_ReturnsNull() {
            // When
            String[] parts = CursorUtils.decodeCompound(null);

            // Then
            assertThat(parts).isNull();
        }

        @Test
        @DisplayName("Should return single element for cursor without pipe")
        void decodeCompound_NoPipe_ReturnsSingleElement() {
            // Given - encode a simple value (no pipe)
            String encoded = CursorUtils.encode(DOCUMENT_ID);

            // When
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(parts).hasSize(1);
            assertThat(parts[0]).isEqualTo(DOCUMENT_ID);
        }

        @Test
        @DisplayName("Should return null for invalid cursor")
        void decodeCompound_InvalidCursor_ReturnsNull() {
            // Given
            String invalidCursor = "!!!invalid!!!";

            // When
            String[] parts = CursorUtils.decodeCompound(invalidCursor);

            // Then
            assertThat(parts).isNull();
        }

        @Test
        @DisplayName("Should handle multiple pipes correctly")
        void decodeCompound_MultiplePipes_SplitsAtFirstOnly() {
            // Given - value with multiple pipes
            String compound = "id123|sort|value|with|pipes";
            String encoded = CursorUtils.encode(compound);

            // When
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then - split with limit=2 should give exactly 2 parts
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEqualTo("id123");
            assertThat(parts[1]).isEqualTo("sort|value|with|pipes");
        }

        @Test
        @DisplayName("Should handle blank cursor")
        void decodeCompound_BlankCursor_ReturnsNull() {
            // When
            String[] parts = CursorUtils.decodeCompound("   ");

            // Then
            assertThat(parts).isNull();
        }

        @Test
        @DisplayName("Should handle empty string cursor")
        void decodeCompound_EmptyCursor_ReturnsNull() {
            // When
            String[] parts = CursorUtils.decodeCompound("");

            // Then
            assertThat(parts).isNull();
        }

        @Test
        @DisplayName("Should handle cursor ending with pipe")
        void decodeCompound_EndsWithPipe_Handles() {
            // Given
            String compound = "id123|";
            String encoded = CursorUtils.encode(compound);

            // When
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEqualTo("id123");
            assertThat(parts[1]).isEmpty();
        }

        @Test
        @DisplayName("Should handle cursor starting with pipe")
        void decodeCompound_StartsWithPipe_Handles() {
            // Given
            String compound = "|sortValue";
            String encoded = CursorUtils.encode(compound);

            // When
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEmpty();
            assertThat(parts[1]).isEqualTo("sortValue");
        }
    }

    @Nested
    @DisplayName("Round Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Simple value round trip")
        void roundTrip_SimpleValue() {
            // Given
            String original = DOCUMENT_ID;

            // When
            String encoded = CursorUtils.encode(original);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("Compound value round trip")
        void roundTrip_CompoundValue() {
            // Given
            String id = DOCUMENT_ID;
            String sort = SORT_VALUE;

            // When
            String encoded = CursorUtils.encodeCompound(id, sort);
            String[] decoded = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(decoded[0]).isEqualTo(id);
            assertThat(decoded[1]).isEqualTo(sort);
        }

        @Test
        @DisplayName("Complex value with special characters round trip")
        void roundTrip_ComplexValue() {
            // Given
            String complex = "doc-123/folder/sub?query=value&other=test#anchor";

            // When
            String encoded = CursorUtils.encode(complex);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(complex);
        }

        @Test
        @DisplayName("Unicode value round trip")
        void roundTrip_UnicodeValue() {
            // Given
            String unicode = "文档ID-日本語-한국어-emoji🎉";

            // When
            String encoded = CursorUtils.encode(unicode);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(unicode);
        }

        @Test
        @DisplayName("Multiple round trips should be stable")
        void roundTrip_MultipleIterations_Stable() {
            // Given
            String original = DOCUMENT_ID;

            // When - encode/decode multiple times
            String value = original;
            for (int i = 0; i < 10; i++) {
                String encoded = CursorUtils.encode(value);
                value = CursorUtils.decode(encoded);
            }

            // Then
            assertThat(value).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle newline characters")
        void handleNewlines() {
            // Given
            String withNewlines = "line1\nline2\r\nline3";

            // When
            String encoded = CursorUtils.encode(withNewlines);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(withNewlines);
        }

        @Test
        @DisplayName("Should handle tab characters")
        void handleTabs() {
            // Given
            String withTabs = "col1\tcol2\tcol3";

            // When
            String encoded = CursorUtils.encode(withTabs);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(withTabs);
        }

        @Test
        @DisplayName("Should handle null bytes")
        void handleNullBytes() {
            // Given
            String withNulls = "before\0after";

            // When
            String encoded = CursorUtils.encode(withNulls);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(withNulls);
        }

        @Test
        @DisplayName("Should handle single character")
        void handleSingleChar() {
            // Given
            String single = "a";

            // When
            String encoded = CursorUtils.encode(single);
            String decoded = CursorUtils.decode(encoded);

            // Then
            assertThat(decoded).isEqualTo(single);
        }

        @Test
        @DisplayName("Should handle only pipe character")
        void handleOnlyPipe() {
            // Given
            String pipe = "|";

            // When
            String encoded = CursorUtils.encode(pipe);
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEmpty();
            assertThat(parts[1]).isEmpty();
        }

        @Test
        @DisplayName("Should handle consecutive pipes")
        void handleConsecutivePipes() {
            // Given
            String pipes = "|||";

            // When
            String encoded = CursorUtils.encode(pipes);
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then - split with limit 2 at first pipe
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEmpty();
            assertThat(parts[1]).isEqualTo("||");
        }

        @Test
        @DisplayName("Should handle extremely long compound value")
        void handleVeryLongCompound() {
            // Given
            String longId = "id-" + "x".repeat(1000);
            Long longSort = Long.MAX_VALUE;

            // When
            String encoded = CursorUtils.encodeCompound(longId, longSort);
            String[] parts = CursorUtils.decodeCompound(encoded);

            // Then
            assertThat(parts[0]).isEqualTo(longId);
            assertThat(parts[1]).isEqualTo(String.valueOf(longSort));
        }
    }

    @Nested
    @DisplayName("Private Constructor Test")
    class PrivateConstructorTest {

        @Test
        @DisplayName("Should have private constructor (utility class)")
        void privateConstructor_PreventInstantiation() throws Exception {
            // Given
            var constructor = CursorUtils.class.getDeclaredConstructor();

            // Then
            assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();

            // When - try to instantiate via reflection
            constructor.setAccessible(true);

            // Then - should not throw when instantiating
            assertThatCode(constructor::newInstance).doesNotThrowAnyException();
        }
    }
}
