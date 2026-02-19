package com.teamsync.presence.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for Permission Manager Service HTTP client.
 */
@Configuration
public class PermissionClientConfig {

    @Value("${teamsync.services.permission-manager-service:http://teamsync-permission-manager-service:9096}")
    private String permissionServiceUrl;

    @Bean
    public PermissionClient permissionClient() {
        RestClient restClient = RestClient.builder()
                .baseUrl(permissionServiceUrl)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(PermissionClient.class);
    }
}
