package com.qligent.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
		String baseUrl,
		String model,
		int numCtx,
		String defaultPromptLocation,
		String promptTemplatesDir,
		String settingsFile
) {

	public LlmProperties {
		if (defaultPromptLocation == null || defaultPromptLocation.isBlank()) {
			defaultPromptLocation = "classpath:default_llm_prompt.md";
		}
		if (promptTemplatesDir == null || promptTemplatesDir.isBlank()) {
			promptTemplatesDir = "./llm/prompt_templates";
		}
		if (settingsFile == null || settingsFile.isBlank()) {
			settingsFile = "./llm/llm-settings.json";
		}
		if (numCtx <= 0) {
			numCtx = 8192;
		}
	}
}
