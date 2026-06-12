package com.qligent.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraProperties(
		String apiUrl,
		String email,
		String apiToken
) {

	public boolean isConfigured() {
		return apiUrl != null && !apiUrl.isBlank()
				&& email != null && !email.isBlank()
				&& apiToken != null && !apiToken.isBlank();
	}
}
