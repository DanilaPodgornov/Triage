package com.qligent.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
		String baseUrl,
		String model,
		String defaultPromptLocation
) {

	public LlmProperties {
		if (defaultPromptLocation == null || defaultPromptLocation.isBlank()) {
			defaultPromptLocation = "classpath:llm/default-prioritization-prompt.txt";
		}
	}
}
