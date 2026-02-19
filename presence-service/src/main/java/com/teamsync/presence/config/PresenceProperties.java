package com.teamsync.presence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "teamsync.presence")
public class PresenceProperties {

    private int heartbeatIntervalSeconds = 30;

    private int timeoutSeconds = 120;

    private int awayThresholdSeconds = 300;  // 5 minutes

    private int documentIdleThresholdSeconds = 60;

    private int cleanupIntervalSeconds = 60;

    private int maxViewersPerDocument = 100;

    private int maxEditorsPerDocument = 10;

    private boolean enableWebSocket = true;

    private boolean publishToKafka = true;

    private List<String> cursorColors = List.of(
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
            "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9",
            "#F8B500", "#00CED1", "#FF69B4", "#32CD32", "#FFD700"
    );
}
