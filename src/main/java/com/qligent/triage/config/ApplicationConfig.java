package com.qligent.triage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ ZephyrProperties.class, LlmProperties.class, TriageProperties.class })
public class ApplicationConfig {

	@Bean
	RestClient zephyrRestClient(ZephyrProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.apiUrl())
				.defaultHeader("Authorization", "Bearer " + properties.apiToken())
				.defaultHeader("Accept", "application/json")
				.build();
	}

	@Bean
	RestClient llmRestClient(LlmProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.defaultHeader("Accept", "application/json")
				.build();
	}

}
