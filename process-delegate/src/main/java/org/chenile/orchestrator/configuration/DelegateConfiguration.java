package org.chenile.orchestrator.configuration;

import org.chenile.orchestrator.delegate.ProcessManagerClient;
import org.chenile.orchestrator.delegate.ProcessManagerClientImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

@Configuration
public class DelegateConfiguration {

    @Bean
    public ProcessManagerClient processManagerClient(RestTemplate restTemplate){
        return new ProcessManagerClientImpl(restTemplate);
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }

}
