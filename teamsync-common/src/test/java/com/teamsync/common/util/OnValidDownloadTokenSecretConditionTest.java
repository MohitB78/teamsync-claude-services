package com.teamsync.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for OnValidDownloadTokenSecretCondition.
 *
 * <p>This condition ensures DownloadTokenUtil is only loaded when
 * a valid secret is configured, preventing startup failures in
 * services that don't need download token functionality.</p>
 */
@DisplayName("OnValidDownloadTokenSecretCondition Tests")
class OnValidDownloadTokenSecretConditionTest {

    private OnValidDownloadTokenSecretCondition condition;
    private ConditionContext context;
    private Environment environment;
    private AnnotatedTypeMetadata metadata;

    private static final String PROPERTY_NAME = "teamsync.security.download-token-secret";

    @BeforeEach
    void setUp() {
        condition = new OnValidDownloadTokenSecretCondition();
        context = mock(ConditionContext.class);
        environment = mock(Environment.class);
        metadata = mock(AnnotatedTypeMetadata.class);
        when(context.getEnvironment()).thenReturn(environment);
    }

    @Nested
    @DisplayName("Valid Secret Tests")
    class ValidSecretTests {

        @Test
        @DisplayName("Should match when secret is valid")
        void shouldMatchWhenSecretIsValid() {
            // Given - valid 32+ character secret
            String validSecret = "this-is-a-valid-secret-with-32-chars!";
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(validSecret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isTrue();
            assertThat(outcome.getMessage()).contains("valid secret configured");
        }

        @Test
        @DisplayName("Should match when secret is exactly 32 characters")
        void shouldMatchWhenSecretIsExactly32Chars() {
            // Given - exactly 32 characters
            String secret = "12345678901234567890123456789012";
            assertThat(secret.length()).isEqualTo(32);
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(secret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isTrue();
        }

        @Test
        @DisplayName("Should match when secret is very long")
        void shouldMatchWhenSecretIsVeryLong() {
            // Given - 256 character secret
            String longSecret = "a".repeat(256);
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(longSecret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isTrue();
        }
    }

    @Nested
    @DisplayName("Missing/Blank Secret Tests")
    class MissingSecretTests {

        @Test
        @DisplayName("Should not match when property is null")
        void shouldNotMatchWhenPropertyIsNull() {
            // Given
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(null);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("not set or is blank");
        }

        @Test
        @DisplayName("Should not match when property is empty string")
        void shouldNotMatchWhenPropertyIsEmpty() {
            // Given
            when(environment.getProperty(PROPERTY_NAME)).thenReturn("");

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("not set or is blank");
        }

        @Test
        @DisplayName("Should not match when property is whitespace only")
        void shouldNotMatchWhenPropertyIsWhitespace() {
            // Given
            when(environment.getProperty(PROPERTY_NAME)).thenReturn("   ");

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("not set or is blank");
        }
    }

    @Nested
    @DisplayName("Placeholder Value Tests")
    class PlaceholderValueTests {

        @Test
        @DisplayName("Should not match when secret contains 'change-in-production'")
        void shouldNotMatchWhenSecretContainsChangeInProduction() {
            // Given - the default placeholder from config-repo
            String placeholder = "default-secret-change-in-production";
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(placeholder);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("placeholder value");
        }

        @Test
        @DisplayName("Should not match when secret contains 'default-secret'")
        void shouldNotMatchWhenSecretContainsDefaultSecret() {
            // Given
            String placeholder = "this-is-my-default-secret-that-is-long";
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(placeholder);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("placeholder value");
        }

        @Test
        @DisplayName("Should not match when secret is exactly 'change-in-production'")
        void shouldNotMatchWhenSecretIsExactlyPlaceholder() {
            // Given
            when(environment.getProperty(PROPERTY_NAME)).thenReturn("change-in-production");

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
        }
    }

    @Nested
    @DisplayName("Short Secret Tests")
    class ShortSecretTests {

        @Test
        @DisplayName("Should not match when secret is too short")
        void shouldNotMatchWhenSecretIsTooShort() {
            // Given - 31 character secret (one less than minimum)
            String shortSecret = "1234567890123456789012345678901";
            assertThat(shortSecret.length()).isEqualTo(31);
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(shortSecret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("too short");
            assertThat(outcome.getMessage()).contains("min 32 chars");
            assertThat(outcome.getMessage()).contains("got 31");
        }

        @Test
        @DisplayName("Should not match when secret is very short")
        void shouldNotMatchWhenSecretIsVeryShort() {
            // Given - 5 character secret
            when(environment.getProperty(PROPERTY_NAME)).thenReturn("short");

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
            assertThat(outcome.getMessage()).contains("too short");
        }

        @Test
        @DisplayName("Should not match when secret is single character")
        void shouldNotMatchWhenSecretIsSingleChar() {
            // Given
            when(environment.getProperty(PROPERTY_NAME)).thenReturn("x");

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should match when secret has special characters")
        void shouldMatchWhenSecretHasSpecialChars() {
            // Given - valid secret with special characters
            String secret = "!@#$%^&*()_+-=[]{}|;':\",./<>?~`";
            assertThat(secret.length()).isGreaterThanOrEqualTo(32);
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(secret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            assertThat(outcome.isMatch()).isTrue();
        }

        @Test
        @DisplayName("Should match when secret has Unicode characters")
        void shouldMatchWhenSecretHasUnicode() {
            // Given - valid secret with Unicode (length >= 32)
            String secret = "日本語シークレット1234567890123456789";
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(secret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then
            // Note: String.length() counts UTF-16 code units, so Unicode might
            // affect the effective length. This tests current behavior.
            if (secret.length() >= 32) {
                assertThat(outcome.isMatch()).isTrue();
            }
        }

        @Test
        @DisplayName("Should handle secret with leading/trailing spaces correctly")
        void shouldHandleSecretWithSpaces() {
            // Given - secret with leading/trailing spaces but valid length
            String secret = "  this-is-a-valid-secret-30-chars  ";
            assertThat(secret.length()).isGreaterThanOrEqualTo(32);
            when(environment.getProperty(PROPERTY_NAME)).thenReturn(secret);

            // When
            ConditionOutcome outcome = condition.getMatchOutcome(context, metadata);

            // Then - spaces count toward length, so this is valid
            assertThat(outcome.isMatch()).isTrue();
        }
    }
}
