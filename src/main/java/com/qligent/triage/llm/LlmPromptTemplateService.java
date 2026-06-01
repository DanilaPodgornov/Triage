package com.qligent.triage.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.qligent.triage.config.LlmProperties;

@Service
public class LlmPromptTemplateService {

	private final String template;

	public LlmPromptTemplateService(LlmProperties properties, ResourceLoader resourceLoader) {
		this.template = loadTemplate(resourceLoader, properties.defaultPromptLocation());
	}

	public String getTemplate() {
		return template;
	}

	public String format(String inputKeys, String testCasesJson) {
		return template
				.replace("{inputKeys}", inputKeys)
				.replace("{testCasesJson}", testCasesJson);
	}

	private static String loadTemplate(ResourceLoader resourceLoader, String location) {
		try {
			Resource resource = resourceLoader.getResource(location);
			if (!resource.exists()) {
				throw new IllegalStateException("LLM default prompt file not found: " + location);
			}
			return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to load LLM default prompt from " + location, exception);
		}
	}

}
