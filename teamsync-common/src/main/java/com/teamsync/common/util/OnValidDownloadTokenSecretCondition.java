package com.teamsync.common.util;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Custom condition that checks if the download token secret is properly configured.
 *
 * <p>This condition ensures that the DownloadTokenUtil bean is only created when:</p>
 * <ul>
 *   <li>The property {@code teamsync.security.download-token-secret} is present</li>
 *   <li>The value is not blank</li>
 *   <li>The value is at least 32 characters long</li>
 *   <li>The value does not contain placeholder text like "change-in-production" or "default-secret"</li>
 * </ul>
 *
 * <p>Services that don't need download token functionality (e.g., Permission Manager)
 * will skip loading this bean entirely, avoiding startup failures.</p>
 */
public class OnValidDownloadTokenSecretCondition extends SpringBootCondition {

    private static final String PROPERTY_NAME = "teamsync.security.download-token-secret";
    private static final int MINIMUM_SECRET_LENGTH = 32;

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String secret = context.getEnvironment().getProperty(PROPERTY_NAME);

        // Check if property exists and is not blank
        if (secret == null || secret.isBlank()) {
            return ConditionOutcome.noMatch(
                    ConditionMessage.forCondition("OnValidDownloadTokenSecret")
                            .because("property '" + PROPERTY_NAME + "' is not set or is blank"));
        }

        // Check for placeholder values
        if (secret.contains("change-in-production") || secret.contains("default-secret")) {
            return ConditionOutcome.noMatch(
                    ConditionMessage.forCondition("OnValidDownloadTokenSecret")
                            .because("property '" + PROPERTY_NAME + "' contains placeholder value"));
        }

        // Check minimum length
        if (secret.length() < MINIMUM_SECRET_LENGTH) {
            return ConditionOutcome.noMatch(
                    ConditionMessage.forCondition("OnValidDownloadTokenSecret")
                            .because("property '" + PROPERTY_NAME + "' is too short (min " +
                                    MINIMUM_SECRET_LENGTH + " chars, got " + secret.length() + ")"));
        }

        return ConditionOutcome.match(
                ConditionMessage.forCondition("OnValidDownloadTokenSecret")
                        .foundExactly("valid secret configured"));
    }
}
