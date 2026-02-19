package com.teamsync.common.permission;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for the Permission Manager HTTP client.
 * Creates the PermissionClient bean using Spring's HTTP interface.
 */
@Configuration
public class PermissionClientConfig {

    @Value("${teamsync.permission-manager.url:http://localhost:9096}")
    private String permissionManagerUrl;

    @Value("${teamsync.permission.client.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${teamsync.permission.client.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public PermissionClient permissionClient() {
        // Configure timeouts
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        // Build RestClient with base URL
        RestClient restClient = RestClient.builder()
                .baseUrl(permissionManagerUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json")
                .build();

        // Create HTTP service proxy
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(adapter)
                .build();

        return factory.createClient(PermissionClient.class);
    }
}
