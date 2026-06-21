package com.qligent.triage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ ZephyrProperties.class, LlmProperties.class, TriageProperties.class, JiraProperties.class })
public class ApplicationConfig {

	@Bean
	RestClient zephyrRestClient(ZephyrProperties properties, RuntimeCredentials runtimeCredentials) {
		ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
			runtimeCredentials.zephyrApiToken()
					.ifPresent(token -> request.getHeaders().setBearerAuth(token));
			return execution.execute(request, body);
		};
		return RestClient.builder()
				.baseUrl(properties.apiUrl())
				.defaultHeader("Accept", "application/json")
				.requestInterceptor(authInterceptor)
				.build();
	}

	@Bean
	RestClient llmRestClient(LlmProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.defaultHeader("Accept", "application/json")
				.build();
	}

	@Bean(name = "jiraRestClient")
	RestClient jiraRestClient() {
		return RestClient.builder()
				.defaultHeader("Accept", "application/json")
				.build();
	}

}
