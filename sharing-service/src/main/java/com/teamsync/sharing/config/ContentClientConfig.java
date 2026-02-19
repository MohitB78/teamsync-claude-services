package com.teamsync.sharing.config;

import com.teamsync.sharing.client.ContentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for the Content Service HTTP client.
 */
@Configuration
public class ContentClientConfig {

    @Value("${teamsync.services.content-service.url:http://content-service:9081}")
    private String contentServiceUrl;

    @Bean
    public ContentServiceClient contentServiceClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
                .baseUrl(contentServiceUrl)
                .defaultHeader("X-Service-Name", "sharing-service")
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ContentServiceClient.class);
    }
}
