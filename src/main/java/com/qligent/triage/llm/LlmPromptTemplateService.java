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

	private final LlmProperties properties;
	private final ResourceLoader resourceLoader;

	public LlmPromptTemplateService(LlmProperties properties, ResourceLoader resourceLoader) {
		this.properties = properties;
		this.resourceLoader = resourceLoader;
	}

	public String getTemplate() {
		return loadTemplate(LlmPromptTemplateType.WEIGHTS);
	}

	public String format(LlmPromptTemplateType type, String placeholderKey, String placeholderValue) {
		String template = loadTemplate(type);
		return template.replace("{" + placeholderKey + "}", placeholderValue);
	}

	public String formatWeightsPrompt(String testCaseData) {
		return format(LlmPromptTemplateType.WEIGHTS, "testCaseData", testCaseData);
	}

	public String formatAiSortPrompt(String testCasesJson) {
		return format(LlmPromptTemplateType.AI_SORT, "testCasesJson", testCasesJson);
	}

	public String templateResourceName(LlmPromptTemplateType type) {
		return sanitizeModel(properties.model()) + "_ctx" + properties.numCtx() + "_" + type.fileSuffix() + ".md";
	}

	private String loadTemplate(LlmPromptTemplateType type) {
		String location = "classpath:llm_prompt_templates/" + templateResourceName(type);
		try {
			Resource resource = resourceLoader.getResource(location);
			if (!resource.exists()) {
				throw new IllegalStateException("LLM prompt template not found: " + location);
			}
			return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to load LLM prompt template from " + location, exception);
		}
	}

	private static String sanitizeModel(String model) {
		return model.replace(':', '-').replace('/', '-');
	}
}
