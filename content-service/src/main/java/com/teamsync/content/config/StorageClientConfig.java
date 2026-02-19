package com.teamsync.content.config;

import com.teamsync.common.context.TenantContext;
import com.teamsync.content.client.StorageServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Configuration for Storage Service HTTP client.
 * Sets up the RestClient with tenant header propagation.
 */
@Configuration
public class StorageClientConfig {

    @Value("${teamsync.services.storage-url:http://localhost:9083}")
    private String storageServiceUrl;

    @Bean
    public StorageServiceClient storageServiceClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
                .baseUrl(storageServiceUrl)
                .requestInterceptor(tenantHeaderPropagationInterceptor())
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(StorageServiceClient.class);
    }

    /**
     * RestClient bean for direct file uploads (multipart).
     * Used by StorageServiceClient for uploadFileDirect method.
     */
    @Bean
    @Qualifier("storageRestClient")
    public RestClient storageRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl(storageServiceUrl)
                .requestInterceptor(tenantHeaderPropagationInterceptor())
                .build();
    }

    /**
     * Interceptor that propagates tenant context headers to downstream services.
     * This ensures Storage Service receives the same tenant/user/drive context.
     */
    private ClientHttpRequestInterceptor tenantHeaderPropagationInterceptor() {
        return (request, body, execution) -> {
            String tenantId = TenantContext.getTenantId();
            String userId = TenantContext.getUserId();
            String driveId = TenantContext.getDriveId();

            if (tenantId != null) {
                request.getHeaders().set("X-Tenant-ID", tenantId);
            }
            if (userId != null) {
                request.getHeaders().set("X-User-ID", userId);
            }
            if (driveId != null) {
                request.getHeaders().set("X-Drive-ID", driveId);
            }

            return execution.execute(request, body);
        };
    }
}
